"""S01–S06: SQLite dùng cho demo độc lập; có thể thay bằng lớp dữ liệu của Dũng."""
import calendar
import hashlib
import json
import sqlite3
from contextlib import contextmanager
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4
from .models import validate, CATEGORIES


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


# Cột thêm sau bản đầu. DB cũ được bổ sung khi mở; dữ liệu cũ giữ nguyên, cột mới là NULL.
MIGRATIONS = (("expenses", "merchant", "TEXT"), ("bills", "end_month", "TEXT"))


def migrate(db):
    for table, column, declaration in MIGRATIONS:
        if column not in {row[1] for row in db.execute(f"PRAGMA table_info({table})")}:
            try:
                db.execute(f"ALTER TABLE {table} ADD COLUMN {column} {declaration}")
            except sqlite3.OperationalError as exc:
                if "duplicate column" not in str(exc).lower():  # tiến trình khác vừa thêm cùng cột
                    raise


def write_fields(intent, fields):
    """Trường được ghi cho một thao tác. merchant chỉ có mặt khi có giá trị, để khóa chống lặp của
    yêu cầu không có cửa hàng giữ nguyên dấu vân tay như trước khi có cột này."""
    names = ("date", "amount_vnd", "description", "category_id") if intent == "record_expense" else ("name", "amount_vnd", "due_day", "start_month")
    data = {name: fields.get(name) for name in names}
    if intent == "record_expense":
        merchant = fields.get("merchant")
        if isinstance(merchant, str):
            merchant = merchant.strip() or None
        if merchant is not None:
            data["merchant"] = merchant
    return data


def insert_record(db, record_id, intent, data):
    # INSERT luôn ghi rõ tên cột: thứ tự cột của DB cũ/mới có thể khác nhau sau migration.
    if intent == "record_expense":
        db.execute("INSERT INTO expenses (id, date, amount_vnd, description, category_id, merchant) VALUES (?,?,?,?,?,?)",
                   (record_id, data["date"], data["amount_vnd"], data["description"], data["category_id"], data.get("merchant")))
    else:
        db.execute("INSERT INTO bills (id, name, amount_vnd, due_day, start_month) VALUES (?,?,?,?,?)",
                   (record_id, data["name"], data["amount_vnd"], data["due_day"], data["start_month"]))


class SQLiteStore:
    # S01: tạo bảng nếu chưa có; không xóa dữ liệu cũ khi khởi động.
    def __init__(self, path):
        self.path = str(path)
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connection() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY, body TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS operations (
                    key TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, receipt TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT PRIMARY KEY, date TEXT NOT NULL, amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0),
                    description TEXT NOT NULL, category_id TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS bills (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL, amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0),
                    due_day INTEGER NOT NULL CHECK(due_day BETWEEN 1 AND 31), start_month TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS paid_periods (
                    period_id TEXT PRIMARY KEY);
            """)
            migrate(db)

    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    # S02: phiên hội thoại tồn tại sau tải lại trình duyệt hoặc khởi động app.
    def load_session(self, session_id):
        with self.connection() as db:
            row = db.execute("SELECT body FROM sessions WHERE id=?", (session_id,)).fetchone()
        return json.loads(row[0]) if row else {
            "draft": None, "state": "idle", "history": [], "pending": None,
            "switch_text": None, "last_receipt": None,
        }

    def save_session(self, session_id, body):
        with self.connection() as db:
            db.execute("INSERT INTO sessions (id, body) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body",
                       (session_id, encode(body)))

    # S03: bản ghi nghiệp vụ và biên nhận được commit cùng một transaction.
    def commit(self, key, intent, fields, today):
        if intent not in {"record_expense", "create_recurring_bill"}:
            raise ValueError("Không phải thao tác ghi được hỗ trợ.")
        data = write_fields(intent, fields)
        fingerprint = hashlib.sha256(encode([intent, data]).encode()).hexdigest()
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM operations WHERE key=?", (key,)).fetchone()
            if row:
                if row["fingerprint"] != fingerprint:
                    raise ValueError("Cùng khóa nhưng nội dung khác. Không được ghi lại.")
                return json.loads(row["receipt"])
            errors = validate(intent, data, today)
            if errors:
                raise ValueError(" ".join(errors.values()))
            record_id = uuid4().hex
            insert_record(db, record_id, intent, data)
            receipt = {"id": record_id, "intent": intent, "data": data}
            db.execute("INSERT INTO operations (key, fingerprint, receipt) VALUES (?,?,?)", (key, fingerprint, encode(receipt)))
        return receipt

    def operation_status(self, key):
        with self.connection() as db:
            row = db.execute("SELECT receipt FROM operations WHERE key=?", (key,)).fetchone()
        return json.loads(row[0]) if row else None

    # S04: chỉ có giao dịch đã commit trong expenses; nháp nằm ở sessions.
    def summary(self, month, category_id=None):
        sql = "SELECT category_id, SUM(amount_vnd) AS total, COUNT(*) AS count FROM expenses WHERE substr(date,1,7)=?"
        params = [month]
        if category_id:
            if category_id not in CATEGORIES:
                raise ValueError("Danh mục không hợp lệ.")
            sql += " AND category_id=?"
            params.append(category_id)
        with self.connection() as db:
            rows = db.execute(sql + " GROUP BY category_id", params).fetchall()
        return {"month": month, "total": sum(r["total"] for r in rows),
                "count": sum(r["count"] for r in rows),
                "by_category": {r["category_id"]: r["total"] for r in rows}}

    def expenses(self):
        with self.connection() as db:
            return [dict(r) for r in db.execute("SELECT * FROM expenses ORDER BY date,id")]

    # S05: bộ hóa đơn tham chiếu cho Chat; không có nghiệp vụ thanh toán thật.
    def due_bills(self, today):
        end = today + timedelta(days=6)
        with self.connection() as db:
            bills = db.execute("SELECT * FROM bills").fetchall()
            paid = {r[0] for r in db.execute("SELECT period_id FROM paid_periods")}
        items = []
        for bill in bills:
            year, month = map(int, bill["start_month"].split("-"))
            # Lịch đã ngừng (end_month) không sinh kỳ sau tháng kết thúc.
            last = min((end.year, end.month), tuple(map(int, bill["end_month"].split("-")))) if bill["end_month"] else (end.year, end.month)
            while (year, month) <= last:
                due = date(year, month, min(bill["due_day"], calendar.monthrange(year, month)[1]))
                period_id = f"{bill['id']}:{year:04d}-{month:02d}"
                if due <= end and period_id not in paid:
                    days = (due - today).days
                    status = "Quá hạn" if days < 0 else "Đến hạn hôm nay" if days == 0 else "Sắp đến hạn" if days <= 3 else "Chưa đến hạn"
                    items.append({"period_id": period_id, "name": bill["name"],
                                  "amount_vnd": bill["amount_vnd"], "due_date": due.isoformat(),
                                  "days": days, "status": status})
                year, month = (year + 1, 1) if month == 12 else (year, month + 1)
        return {"start": today.isoformat(), "end": end.isoformat(),
                "items": sorted(items, key=lambda x: (x["due_date"], x["period_id"]))}

    # S06: chỉ dùng cho seed/test; màn hình demo không tự đánh dấu đã trả.
    def seed_paid_period(self, period_id):
        with self.connection() as db:
            db.execute("INSERT OR IGNORE INTO paid_periods (period_id) VALUES (?)", (period_id,))
