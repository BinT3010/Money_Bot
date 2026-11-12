"""Lịch hóa đơn định kỳ: kỳ, trạng thái, nhắc hạn, thanh toán và đối chiếu nghĩa vụ (G3)."""
import calendar
from datetime import date, timedelta
from uuid import uuid4

from common import month_check, shift_month
from money_chat.storage import insert_record


class BillsMixin:
    """Dùng chung connection()/atomic()/overview() của FinanceStore."""

    def periods(self, today):
        """Bao gồm kỳ nợ cũ và mọi kỳ trong tháng hiện tại; ngày 31 được kẹp cuối tháng."""
        with self.connection() as db:
            bills = [dict(r) for r in db.execute("SELECT * FROM bills")]
            paid = {r["period_id"]: dict(r) for r in db.execute("SELECT * FROM payments")}
        result = []
        current = today.strftime("%Y-%m")
        for bill in bills:
            m = bill["start_month"]
            while m <= current:
                y, n = map(int, m.split("-"))
                due = date(y, n, min(bill["due_day"], calendar.monthrange(y, n)[1]))
                pid = f"{bill['id']}:{m}"
                days = (due - today).days
                state = "PAID" if pid in paid else "OVERDUE" if days < 0 else "DUE_TODAY" if days == 0 else "DUE_SOON" if days <= 3 else "UPCOMING"
                result.append({**bill, "period_id": pid, "month": m, "due_date": due.isoformat(), "days": days, "status": state,
                               "expense_id": paid.get(pid, {}).get("expense_id")})
                m = shift_month(m, 1)
        return sorted(result, key=lambda r: (r["due_date"], r["id"]))

    def pay(self, bill_id, month, paid_on, expense_id, today):
        month_check(month)
        try:
            paid_date = date.fromisoformat(paid_on)
        except (ValueError, TypeError):
            paid_date = None
        if paid_date is None or paid_date > today or paid_date.isoformat() != paid_on:
            raise ValueError("Ngày thanh toán phải dạng yyyy-MM-dd và không ở tương lai.")
        # Cho phép trả trước tối đa từ đầu tháng liền trước kỳ; sớm hơn thì khoản chi rơi sai tháng thống kê.
        if paid_on < shift_month(month, -1) + "-01":
            raise ValueError("Ngày thanh toán không được sớm hơn tháng liền trước kỳ hóa đơn.")
        pid = f"{bill_id}:{month}"
        with self.atomic() as db:
            bill = db.execute("SELECT * FROM bills WHERE id=?", (bill_id,)).fetchone()
            if not bill or month < bill["start_month"] or month > today.strftime("%Y-%m"):
                raise ValueError("Không tìm thấy kỳ hóa đơn hợp lệ.")
            old = db.execute("SELECT * FROM payments WHERE period_id=?", (pid,)).fetchone()
            if old:
                return {**dict(old), "already_paid": True}
            if expense_id:
                expense = db.execute("SELECT * FROM expenses WHERE id=?", (expense_id,)).fetchone()
                if not expense or expense["amount_vnd"] != bill["amount_vnd"] or expense["category_id"] != "utilities" or expense["date"] != paid_on:
                    raise ValueError("Giao dịch liên kết phải khớp số tiền, danh mục hóa đơn và ngày thanh toán.")
                if db.execute("SELECT 1 FROM payments WHERE expense_id=?", (expense_id,)).fetchone():
                    raise ValueError("Giao dịch đã liên kết một kỳ khác.")
            else:
                expense_id = uuid4().hex
                insert_record(db, expense_id, "record_expense", {"date": paid_on, "amount_vnd": bill["amount_vnd"],
                                                                 "description": f"{bill['name']} · kỳ {month}", "category_id": "utilities"})
            db.execute("INSERT INTO payments (period_id, expense_id, paid_on) VALUES (?,?,?)", (pid, expense_id, paid_on))
            db.execute("INSERT OR IGNORE INTO paid_periods (period_id) VALUES (?)", (pid,))
        return {"period_id": pid, "expense_id": expense_id, "paid_on": paid_on, "already_paid": False}

    def obligations(self, today):
        month = today.strftime("%Y-%m")
        end = min(today + timedelta(days=6), date(today.year, today.month, calendar.monthrange(today.year, today.month)[1]))
        rows = [r for r in self.periods(today) if r["status"] != "PAID"]
        included = [r for r in rows if r["month"] == month and r["due_date"] <= end.isoformat()]
        older = [r for r in rows if r["month"] < month]
        s = sum(r["amount_vnd"] for r in included)
        remaining = self.overview(month)["remaining"]
        return {"start": today.isoformat(), "end": end.isoformat(), "items": included, "older_unpaid": older,
                "obligations": s, "remaining_budget": remaining, "difference": remaining - s if remaining is not None else None,
                "note": "Chỉ đối chiếu ngân sách theo dữ liệu đã ghi, không phải số dư ngân hàng. Nợ tháng trước hiển thị riêng."}

    def reminders(self, today):
        return [{**r, "notice_id": f"{r['period_id']}:{r['days']}"} for r in self.periods(today)
                if r["status"] != "PAID" and r["days"] in (3, 1, 0)]
