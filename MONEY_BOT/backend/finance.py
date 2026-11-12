"""Kho nghiệp vụ chung. Kế thừa SQLiteStore của Chat, không tạo kho tiền thứ hai."""
import calendar
import hashlib
import json
import sqlite3
from contextlib import contextmanager
from datetime import date, timedelta
from uuid import uuid4

from money_chat.storage import SQLiteStore, encode
from money_chat.models import CATEGORIES, valid_month, validate


def month_check(month):
    if not valid_month(month):
        raise ValueError("Tháng phải có dạng yyyy-MM.")
    return month


def shift_month(month, offset):
    y, m = map(int, month_check(month).split("-"))
    n = y * 12 + m - 1 + offset
    return f"{n // 12:04d}-{n % 12 + 1:02d}"


def integer(value, minimum=0, maximum=10**12):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"Giá trị phải là số nguyên từ {minimum} đến {maximum}.")
    return value


class FinanceStore(SQLiteStore):
    """Mỗi request có một instance; atomic() gộp state Chat và ghi tiền cùng transaction."""
    def __init__(self, path):
        self.active = None
        super().__init__(path)
        with self.connection() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS budgets (
                    month TEXT PRIMARY KEY, total INTEGER NOT NULL,
                    limits TEXT NOT NULL, threshold INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS payments (
                    period_id TEXT PRIMARY KEY, expense_id TEXT UNIQUE NOT NULL,
                    paid_on TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS requests (
                    key TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, response TEXT NOT NULL);
            """)

    @contextmanager
    def connection(self):
        if self.active is not None:
            yield self.active
        else:
            with super().connection() as db:
                yield db

    @contextmanager
    def atomic(self):
        if self.active is not None:
            yield self.active
            return
        with super().connection() as db:
            db.execute("BEGIN IMMEDIATE")
            self.active = db
            try:
                yield db
            finally:
                self.active = None

    def commit(self, key, intent, fields, today):
        # SQLiteStore.commit mở BEGIN riêng. Trong atomic(), áp dụng cùng giao thức
        # biên nhận nhưng không mở transaction lồng nhau.
        if self.active is None:
            return super().commit(key, intent, fields, today)
        if intent not in {"record_expense", "create_recurring_bill"}:
            raise ValueError("Thao tác không hợp lệ.")
        names = ("date", "amount_vnd", "description", "category_id") if intent == "record_expense" else ("name", "amount_vnd", "due_day", "start_month")
        data = {k: fields.get(k) for k in names}
        fingerprint = hashlib.sha256(encode([intent, data]).encode()).hexdigest()
        db = self.active
        old = db.execute("SELECT * FROM operations WHERE key=?", (key,)).fetchone()
        if old:
            if old["fingerprint"] != fingerprint:
                raise ValueError("Khóa đã dùng cho nội dung khác.")
            return json.loads(old["receipt"])
        errors = validate(intent, data, today)
        if errors:
            raise ValueError(" ".join(errors.values()))
        rid = uuid4().hex
        if intent == "record_expense":
            db.execute("INSERT INTO expenses VALUES (?,?,?,?,?)", (rid, data["date"], data["amount_vnd"], data["description"], data["category_id"]))
        else:
            db.execute("INSERT INTO bills VALUES (?,?,?,?,?)", (rid, data["name"], data["amount_vnd"], data["due_day"], data["start_month"]))
        receipt = {"id": rid, "intent": intent, "data": data}
        db.execute("INSERT INTO operations VALUES (?,?,?)", (key, fingerprint, encode(receipt)))
        return receipt

    def once(self, key, payload, action):
        """Cùng key+nội dung trả lại kết quả; khác nội dung từ chối. Crash rollback cả yêu cầu."""
        if not isinstance(key, str) or not 8 <= len(key) <= 160:
            raise ValueError("Cần Idempotency-Key dài 8–160 ký tự.")
        fingerprint = hashlib.sha256(encode(payload).encode()).hexdigest()
        with self.atomic() as db:
            old = db.execute("SELECT * FROM requests WHERE key=?", (key,)).fetchone()
            if old:
                if old["fingerprint"] != fingerprint:
                    raise ValueError("Khóa yêu cầu đã dùng cho nội dung khác.")
                return json.loads(old["response"])
            result = action()
            db.execute("INSERT INTO requests VALUES (?,?,?)", (key, fingerprint, encode(result)))
            return result

    def cached(self, key, payload):
        fingerprint = hashlib.sha256(encode(payload).encode()).hexdigest()
        with self.connection() as db:
            row = db.execute("SELECT * FROM requests WHERE key=?", (key,)).fetchone()
        if row:
            if row["fingerprint"] != fingerprint:
                raise ValueError("Khóa yêu cầu đã dùng cho nội dung khác.")
            return json.loads(row["response"])
        return None

    def update_expense(self, rid, fields, today, delete=False):
        with self.atomic() as db:
            old = db.execute("SELECT * FROM expenses WHERE id=?", (rid,)).fetchone()
            if not old:
                raise ValueError("Không tìm thấy giao dịch.")
            if db.execute("SELECT 1 FROM payments WHERE expense_id=?", (rid,)).fetchone():
                raise ValueError("Giao dịch đã liên kết hóa đơn; không sửa/xóa để tránh sai trạng thái thanh toán.")
            if delete:
                db.execute("DELETE FROM expenses WHERE id=?", (rid,))
                return {"deleted": rid}
            errors = validate("record_expense", fields, today)
            if errors:
                raise ValueError(" ".join(errors.values()))
            db.execute("UPDATE expenses SET date=?,amount_vnd=?,description=?,category_id=? WHERE id=?",
                       (fields["date"], fields["amount_vnd"], fields["description"], fields["category_id"], rid))
            return {"id": rid, **fields}

    def budget(self, month):
        month_check(month)
        with self.connection() as db:
            row = db.execute("SELECT * FROM budgets WHERE month=?", (month,)).fetchone()
        return {"month": month, "total": row["total"] if row else None,
                "limits": json.loads(row["limits"]) if row else {}, "threshold": row["threshold"] if row else 80}

    def set_budget(self, month, total, limits, threshold):
        month_check(month)
        integer(total, 1)
        integer(threshold, 1, 99)
        if not isinstance(limits, dict) or any(k not in CATEGORIES for k in limits):
            raise ValueError("Danh mục không hợp lệ.")
        for value in limits.values():
            integer(value)
        if sum(limits.values()) > total:
            raise ValueError("Tổng hạn mức danh mục vượt ngân sách tổng.")
        with self.connection() as db:
            db.execute("INSERT INTO budgets VALUES (?,?,?,?) ON CONFLICT(month) DO UPDATE SET total=excluded.total, limits=excluded.limits, threshold=excluded.threshold",
                       (month, total, encode(limits), threshold))
        return self.budget(month)

    def overview(self, month):
        b, s = self.budget(month), self.summary(month)
        rows = []
        for cid, label in CATEGORIES.items():
            spent, limit = s["by_category"].get(cid, 0), b["limits"].get(cid)
            level = "UNSET"
            if limit is not None:
                level = ("OVER" if spent > 0 else "NORMAL") if limit == 0 else (
                    "OVER" if spent > limit else "REACHED" if spent == limit else
                    "WARNING" if spent * 100 >= limit * b["threshold"] else "NORMAL")
            rows.append({"category_id": cid, "label": label, "spent": spent, "limit": limit,
                         "level": level, "ratio": spent / limit if limit else None})
        return {**s, "budget": b, "remaining": b["total"] - s["total"] if b["total"] is not None else None, "categories": rows}

    def suggest(self, month, today):
        b = self.budget(month)
        if month > today.strftime("%Y-%m"):
            raise ValueError("Chỉ đề xuất cho tháng hiện tại hoặc tháng đã qua.")
        months = [shift_month(month, -n) for n in (3, 2, 1)]
        with self.connection() as db:
            first = db.execute("SELECT MIN(date) FROM expenses").fetchone()[0]
        if not first or first[:7] > months[0] or b["total"] is None:
            return {"available": False, "months": months, "reason": "Cần ngân sách tổng và lịch sử bắt đầu ít nhất ba tháng trước; hãy nhập hạn mức thủ công."}
        totals = {k: sum(self.summary(m)["by_category"].get(k, 0) for m in months) for k in CATEGORIES}
        aggregate = sum(totals.values())
        if aggregate == 0:
            return {"available": False, "months": months, "reason": "Ba tháng không có chi tiêu; hãy nhập hạn mức thủ công."}
        # Tính bằng số nguyên để floor chính xác; A=sum(totals)/3.
        limits = {k: v // 3 if aggregate <= 3 * b["total"] else v * b["total"] // aggregate for k, v in totals.items()}
        return {"available": True, "months": months, "limits": limits,
                "reason": "Trung bình 3 tháng đầy đủ, co theo ngân sách tổng; tháng không chi tính 0. Dữ liệu nhập được giả định đầy đủ."}

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
        paid_date = date.fromisoformat(paid_on)
        if paid_date > today or paid_date.isoformat() != paid_on:
            raise ValueError("Ngày thanh toán phải dạng yyyy-MM-dd và không ở tương lai.")
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
                db.execute("INSERT INTO expenses VALUES (?,?,?,?,?)", (expense_id, paid_on, bill["amount_vnd"], f"{bill['name']} · kỳ {month}", "utilities"))
            db.execute("INSERT INTO payments VALUES (?,?,?)", (pid, expense_id, paid_on))
            db.execute("INSERT OR IGNORE INTO paid_periods VALUES (?)", (pid,))
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
