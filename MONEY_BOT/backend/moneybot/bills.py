"""Lịch hóa đơn định kỳ: kỳ, trạng thái, nhắc hạn, thanh toán và đối chiếu nghĩa vụ (G3)."""
import calendar
from datetime import date, timedelta
from uuid import uuid4

from moneybot.common import integer, month_check, shift_month
from moneybot.money_chat.models import money, validate
from moneybot.money_chat.storage import insert_record, write_fields


class BillsMixin:
    """Dùng chung connection()/atomic()/overview() của FinanceStore."""

    def periods(self, today, until=None):
        """Bao gồm kỳ nợ cũ và mọi kỳ đến tháng `until` (mặc định tháng hiện tại); ngày 31 được kẹp cuối tháng."""
        with self.connection() as db:
            bills = [dict(r) for r in db.execute("SELECT * FROM bills")]
            # Số tiền thực trả = số tiền của khoản chi đã ghi cho kỳ (có thể khác số tiền dự kiến).
            paid = {r["period_id"]: dict(r) for r in db.execute(
                "SELECT p.period_id, p.expense_id, p.paid_on, e.amount_vnd AS paid_amount "
                "FROM payments p LEFT JOIN expenses e ON e.id = p.expense_id")}
        result = []
        current = until or today.strftime("%Y-%m")
        for bill in bills:
            m = bill["start_month"]
            last = min(current, bill["end_month"]) if bill["end_month"] else current  # lịch đã ngừng
            while m <= last:
                y, n = map(int, m.split("-"))
                due = date(y, n, min(bill["due_day"], calendar.monthrange(y, n)[1]))
                pid = f"{bill['id']}:{m}"
                days = (due - today).days
                state = "PAID" if pid in paid else "OVERDUE" if days < 0 else "DUE_TODAY" if days == 0 else "DUE_SOON" if days <= 3 else "UPCOMING"
                result.append({**bill, "period_id": pid, "month": m, "due_date": due.isoformat(), "days": days, "status": state,
                               "expense_id": paid.get(pid, {}).get("expense_id"),
                               "paid_on": paid.get(pid, {}).get("paid_on"),
                               "paid_amount": paid.get(pid, {}).get("paid_amount")})
                m = shift_month(m, 1)
        return sorted(result, key=lambda r: (r["due_date"], r["id"]))

    # Lịch bắt đầu từ năm 2000 sinh hơn 300 kỳ; danh sách trả về được giới hạn.
    PAID_HISTORY_MONTHS = 12
    MAX_PAGE = 500

    def period_page(self, today, limit=100, offset=0, history="recent"):
        """Một trang danh sách kỳ (thứ tự theo hạn như periods()). Kỳ chưa trả luôn có mặt; kỳ đã trả chỉ lấy
        12 tháng gần nhất trừ khi history='all'. periods_total/has_more cho phép tải tiếp."""
        integer(limit, 1, self.MAX_PAGE)
        integer(offset, 0, 10**6)
        if history not in ("recent", "all"):
            raise ValueError("history phải là recent hoặc all.")
        cutoff = shift_month(today.strftime("%Y-%m"), 1 - self.PAID_HISTORY_MONTHS)
        visible = [p for p in self.periods(today) if p["status"] != "PAID" or history == "all" or p["month"] >= cutoff]
        return {"periods": visible[offset:offset + limit], "periods_total": len(visible),
                "offset": offset, "limit": limit, "has_more": offset + limit < len(visible)}

    @staticmethod
    def _paid_months(db, bill_id):
        return sorted(r[0].rsplit(":", 1)[1] for r in db.execute(
            "SELECT period_id FROM payments WHERE substr(period_id, 1, ?) = ?", (len(bill_id) + 1, bill_id + ":")))

    def update_bill(self, bill_id, fields, today):
        """Sửa lịch (tên, tiền dự kiến, ngày lặp, tháng bắt đầu, tháng kết thúc). Kỳ đã trả phải còn nằm trong lịch.
        Sửa ngày lặp thì các kỳ chưa trả và lời nhắc tính lại theo ngày mới."""
        data = write_fields("create_recurring_bill", fields)
        errors = validate("create_recurring_bill", data, today)
        if errors:
            raise ValueError(" ".join(errors.values()))
        end_month = fields.get("end_month")
        if end_month is not None:
            month_check(end_month)
            if end_month < data["start_month"]:
                raise ValueError("Tháng kết thúc phải từ tháng bắt đầu trở đi.")
        with self.atomic() as db:
            if not db.execute("SELECT 1 FROM bills WHERE id=?", (bill_id,)).fetchone():
                raise ValueError("Không tìm thấy lịch hóa đơn.")
            paid = self._paid_months(db, bill_id)
            if paid and (data["start_month"] > paid[0] or (end_month is not None and end_month < paid[-1])):
                raise ValueError(f"Thay đổi làm mất kỳ đã thanh toán ({paid[0]} – {paid[-1]}). "
                                 "Giữ tháng bắt đầu không sau và tháng kết thúc không trước các kỳ đó.")
            db.execute("UPDATE bills SET name=?, amount_vnd=?, due_day=?, start_month=?, end_month=? WHERE id=?",
                       (data["name"], data["amount_vnd"], data["due_day"], data["start_month"], end_month, bill_id))
            return dict(db.execute("SELECT * FROM bills WHERE id=?", (bill_id,)).fetchone())

    def stop_bill(self, bill_id, today, end_month=None):
        """Ngừng lịch. Chưa có kỳ nào trả: xóa hẳn. Đã có: đặt end_month để giữ lịch sử.
        Mặc định kết thúc ở tháng hiện tại nếu kỳ này đã trả, ngược lại ở tháng trước;
        không bao giờ trước kỳ đã trả cuối cùng. Kỳ chưa trả trước tháng kết thúc vẫn hiển thị là nợ."""
        if end_month is not None:
            month_check(end_month)
        current = today.strftime("%Y-%m")
        with self.atomic() as db:
            bill = db.execute("SELECT * FROM bills WHERE id=?", (bill_id,)).fetchone()
            if not bill:
                raise ValueError("Không tìm thấy lịch hóa đơn.")
            paid = self._paid_months(db, bill_id)
            if not paid:
                db.execute("DELETE FROM bills WHERE id=?", (bill_id,))
                db.execute("DELETE FROM paid_periods WHERE substr(period_id, 1, ?) = ?", (len(bill_id) + 1, bill_id + ":"))
                return {"id": bill_id, "deleted": True, "end_month": None}
            if end_month is None:
                default = current if current in paid else shift_month(current, -1)
                end_month = max(default, paid[-1])  # kỳ trả trước (tháng sau) vẫn nằm trong lịch
            elif end_month < paid[-1]:
                raise ValueError(f"Tháng kết thúc không được trước kỳ đã thanh toán {paid[-1]}.")
            db.execute("UPDATE bills SET end_month=? WHERE id=?", (end_month, bill_id))
            return {"id": bill_id, "deleted": False, "end_month": end_month}

    def pay(self, bill_id, month, paid_on, expense_id, today, amount_vnd=None):
        """Xác nhận trả một kỳ. Liên kết khoản chi đã ghi (tiền thực trả = tiền khoản chi đó),
        hoặc tạo khoản chi mới bằng amount_vnd (mặc định = số tiền dự kiến của lịch)."""
        month_check(month)
        if amount_vnd is not None:
            integer(amount_vnd, 1)
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
            if (not bill or month < bill["start_month"] or month > today.strftime("%Y-%m")
                    or (bill["end_month"] and month > bill["end_month"])):
                raise ValueError("Không tìm thấy kỳ hóa đơn hợp lệ.")
            old = db.execute("SELECT p.period_id, p.expense_id, p.paid_on, e.amount_vnd AS paid_amount "
                             "FROM payments p LEFT JOIN expenses e ON e.id = p.expense_id WHERE p.period_id=?", (pid,)).fetchone()
            if old:
                return {**dict(old), "already_paid": True}
            if expense_id:
                # Khoản chi đã ghi: không tăng chi lần nữa; tiền thực trả có thể khác tiền dự kiến.
                expense = db.execute("SELECT * FROM expenses WHERE id=?", (expense_id,)).fetchone()
                if not expense or expense["category_id"] != "utilities" or expense["date"] != paid_on:
                    raise ValueError("Giao dịch liên kết phải thuộc danh mục Hóa đơn tiện ích và có ngày trùng ngày thanh toán.")
                if amount_vnd is not None and amount_vnd != expense["amount_vnd"]:
                    raise ValueError("Số tiền thực trả phải bằng số tiền của giao dịch được liên kết.")
                if db.execute("SELECT 1 FROM payments WHERE expense_id=?", (expense_id,)).fetchone():
                    raise ValueError("Giao dịch đã liên kết một kỳ khác.")
                paid_amount = expense["amount_vnd"]
            else:
                expense_id = uuid4().hex
                paid_amount = bill["amount_vnd"] if amount_vnd is None else amount_vnd
                insert_record(db, expense_id, "record_expense", {"date": paid_on, "amount_vnd": paid_amount,
                                                                 "description": f"{bill['name']} · kỳ {month}", "category_id": "utilities"})
            db.execute("INSERT INTO payments (period_id, expense_id, paid_on) VALUES (?,?,?)", (pid, expense_id, paid_on))
            db.execute("INSERT OR IGNORE INTO paid_periods (period_id) VALUES (?)", (pid,))
        return {"period_id": pid, "expense_id": expense_id, "paid_on": paid_on, "paid_amount": paid_amount, "already_paid": False}

    def obligations(self, today):
        """Ou7: R = B − C (ngân sách tháng trừ chi đã xác nhận); S = hóa đơn chưa trả của tháng này có hạn đến
        hết cửa sổ [hôm nay, min(hôm nay+6, cuối tháng)], kể cả kỳ quá hạn trong tháng. Hóa đơn tháng sau trong
        sáu ngày tới và nợ tháng trước được liệt kê riêng, không cộng vào S."""
        month = today.strftime("%Y-%m")
        horizon = today + timedelta(days=6)
        end = min(horizon, date(today.year, today.month, calendar.monthrange(today.year, today.month)[1]))
        next_month = shift_month(month, 1)
        rows = [r for r in self.periods(today, until=next_month) if r["status"] != "PAID"]
        included = [r for r in rows if r["month"] == month and r["due_date"] <= end.isoformat()]
        older = [r for r in rows if r["month"] < month]
        upcoming = [r for r in rows if r["month"] == next_month and r["due_date"] <= horizon.isoformat()]
        s = sum(r["amount_vnd"] for r in included)
        overview = self.overview(month)
        budget_total, spent, remaining = overview["budget"]["total"], overview["total"], overview["remaining"]
        if remaining is None:
            status, shortfall, surplus = "UNKNOWN", None, None
            message = (f"Chưa có ngân sách tổng tháng {month}. Tổng hóa đơn đang xét là {money(s)}; "
                       "hãy nhập ngân sách để đối chiếu. Chưa kết luận đủ hay thiếu.")
        elif s > remaining:
            status, shortfall, surplus = "SHORT", s - remaining, 0
            message = (f"Theo dữ liệu đã ghi, ngân sách còn lại thấp hơn tổng hóa đơn đang xét {money(shortfall)}. "
                       "Hãy kiểm tra các khoản chi và kế hoạch thanh toán.")
        else:
            status, shortfall, surplus = "ENOUGH", 0, remaining - s
            message = ("Không có hóa đơn chưa trả trong cửa sổ này. " if not included else "") + (
                f"Theo dữ liệu đã ghi, ngân sách còn lại đủ cho các hóa đơn đang xét"
                f"{' (vừa đủ)' if surplus == 0 else f', còn dư {money(surplus)}'}.")
        excluded = []
        if upcoming:
            excluded.append(f"{len(upcoming)} hóa đơn tháng {next_month} ({money(sum(r['amount_vnd'] for r in upcoming))})")
        if older:
            excluded.append(f"{len(older)} kỳ nợ tháng trước ({money(sum(r['amount_vnd'] for r in older))})")
        # Nợ cũ có thể rất dài: trả 50 kỳ gần nhất, kèm tổng số kỳ và tổng tiền của tất cả.
        return {"start": today.isoformat(), "end": end.isoformat(), "items": included, "older_unpaid": older[-50:],
                "older_unpaid_count": len(older), "older_unpaid_total": sum(r["amount_vnd"] for r in older),
                "next_month": upcoming, "budget_total": budget_total, "spent": spent,
                "obligations": s, "remaining_budget": remaining, "difference": remaining - s if remaining is not None else None,
                "status": status, "shortfall": shortfall, "surplus": surplus, "message": message,
                "excluded": ("Chưa gộp vào phép đối chiếu: " + "; ".join(excluded) + ".") if excluded else None,
                "note": "Chỉ đối chiếu ngân sách theo dữ liệu đã ghi, không phải số dư ngân hàng. Nợ tháng trước hiển thị riêng."}

    def reminders(self, today):
        """Mốc nhắc 3 ngày, 1 ngày, hôm nay. Xét cả kỳ tháng sau: cuối tháng, hóa đơn hạn ngày 1–3 tháng sau
        phải được nhắc trước (giáp ranh tháng/năm). notice_id = kỳ + mốc, nên làm mới không tạo thông báo trùng."""
        next_month = shift_month(today.strftime("%Y-%m"), 1)
        return [{**r, "notice_id": f"{r['period_id']}:{r['days']}"} for r in self.periods(today, until=next_month)
                if r["status"] != "PAID" and r["days"] in (3, 1, 0)]
