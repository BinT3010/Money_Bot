"""Kho nghiệp vụ chung. Kế thừa SQLiteStore của Chat, không tạo kho tiền thứ hai.

Phần lõi (giao dịch, idempotency, transaction) ở đây; ngân sách ở budget.py, hóa đơn ở bills.py.
"""
import hashlib
import json
from contextlib import contextmanager
from uuid import uuid4

from bills import BillsMixin
from budget import BudgetMixin
from common import month_check, shift_month, integer  # noqa: F401 — giữ tên cũ cho nơi import từ finance
from money_chat.storage import SQLiteStore, encode, insert_record, write_fields
from money_chat.models import validate


class FinanceStore(BillsMixin, BudgetMixin, SQLiteStore):
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
        data = write_fields(intent, fields)
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
        insert_record(db, rid, intent, data)
        receipt = {"id": rid, "intent": intent, "data": data}
        db.execute("INSERT INTO operations (key, fingerprint, receipt) VALUES (?,?,?)", (key, fingerprint, encode(receipt)))
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
            db.execute("INSERT INTO requests (key, fingerprint, response) VALUES (?,?,?)", (key, fingerprint, encode(result)))
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
            data = write_fields("record_expense", fields)
            errors = validate("record_expense", data, today)
            if errors:
                raise ValueError(" ".join(errors.values()))
            # Không gửi merchant = giữ cửa hàng đã lưu (client cũ không biết trường này);
            # gửi chuỗi rỗng hoặc null tường minh = xóa cửa hàng.
            merchant = data.get("merchant") if "merchant" in fields else old["merchant"]
            db.execute("UPDATE expenses SET date=?,amount_vnd=?,description=?,category_id=?,merchant=? WHERE id=?",
                       (data["date"], data["amount_vnd"], data["description"], data["category_id"], merchant, rid))
            return {"id": rid, **{k: data[k] for k in ("date", "amount_vnd", "description", "category_id")}, "merchant": merchant}
