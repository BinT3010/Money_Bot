"""D02: migration schema (merchant, end_month), INSERT ghi rõ tên cột, hợp đồng trường merchant."""
import sqlite3
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app
from finance import FinanceStore
from money_chat.models import validate
from money_chat.storage import SQLiteStore

TODAY = date(2026, 11, 12)

# Schema đúng như bản trước migration (để mô phỏng DB người dùng đang có).
LEGACY = """
CREATE TABLE sessions (id TEXT PRIMARY KEY, body TEXT NOT NULL);
CREATE TABLE operations (key TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, receipt TEXT NOT NULL);
CREATE TABLE expenses (id TEXT PRIMARY KEY, date TEXT NOT NULL, amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0),
    description TEXT NOT NULL, category_id TEXT NOT NULL);
CREATE TABLE bills (id TEXT PRIMARY KEY, name TEXT NOT NULL, amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0),
    due_day INTEGER NOT NULL CHECK(due_day BETWEEN 1 AND 31), start_month TEXT NOT NULL);
CREATE TABLE paid_periods (period_id TEXT PRIMARY KEY);
"""


def columns(path, table):
    with sqlite3.connect(path) as db:
        return [r[1] for r in db.execute(f"PRAGMA table_info({table})")]


def test_legacy_db_gains_columns_and_keeps_data(tmp_path):
    path = tmp_path / "legacy.sqlite3"
    with sqlite3.connect(path) as db:
        db.executescript(LEGACY)
        db.execute("INSERT INTO expenses VALUES ('e1','2026-11-01',45000,'Ăn trưa','food')")
        db.execute("INSERT INTO bills VALUES ('b1','Điện',500000,20,'2026-10')")
    s = FinanceStore(path)
    assert columns(path, "expenses")[-1] == "merchant"
    assert columns(path, "bills")[-1] == "end_month"
    assert s.summary("2026-11")["total"] == 45000
    assert s.expenses()[0]["merchant"] is None
    assert [p["end_month"] for p in s.periods(TODAY)] == [None, None]
    # Mở lại nhiều lần không lỗi, không thêm cột trùng.
    FinanceStore(path)
    SQLiteStore(path)
    assert columns(path, "expenses").count("merchant") == 1


def test_insert_uses_column_names_not_positions(tmp_path):
    # Bảng có cột theo thứ tự khác: INSERT theo vị trí sẽ ghi lệch cột.
    path = tmp_path / "reordered.sqlite3"
    with sqlite3.connect(path) as db:
        db.executescript(LEGACY.replace(
            "CREATE TABLE expenses (id TEXT PRIMARY KEY, date TEXT NOT NULL, amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0),\n    description TEXT NOT NULL, category_id TEXT NOT NULL);",
            "CREATE TABLE expenses (id TEXT PRIMARY KEY, merchant TEXT, category_id TEXT NOT NULL, description TEXT NOT NULL,\n    amount_vnd INTEGER NOT NULL CHECK(amount_vnd > 0), date TEXT NOT NULL);"))
    s = FinanceStore(path)
    s.commit("k-reordered", "record_expense",
             {"date": "2026-11-02", "amount_vnd": 30000, "description": "Cà phê", "category_id": "food", "merchant": "Highlands"}, TODAY)
    row = s.expenses()[0]
    assert (row["date"], row["amount_vnd"], row["category_id"], row["merchant"]) == ("2026-11-02", 30000, "food", "Highlands")


def test_validate_merchant():
    base = {"date": "2026-11-01", "amount_vnd": 1, "description": "x", "category_id": "food"}
    assert validate("record_expense", base, TODAY) == {}
    assert validate("record_expense", {**base, "merchant": "Bách Hóa Xanh"}, TODAY) == {}
    assert "merchant" in validate("record_expense", {**base, "merchant": "a" * 121}, TODAY)
    assert "merchant" in validate("record_expense", {**base, "merchant": 5}, TODAY)


@pytest.fixture
def client(tmp_path):
    c = TestClient(create_app(tmp_path, lambda: TODAY))
    token = c.post("/api/auth/register", json={"email": "m@test.vn", "password": "Password123"}).json()["token"]
    c.headers["Authorization"] = f"Bearer {token}"
    return c


def write(c, method, path, body, key=None):
    return c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": key or uuid4().hex})


EXPENSE = {"amount_vnd": 120000, "description": "Đi chợ", "category_id": "shopping", "date": "2026-11-10"}


def test_merchant_round_trip_and_edit_rules(client):
    rid = write(client, "POST", "/transactions", {**EXPENSE, "merchant": "  Bách Hóa Xanh  "}).json()["id"]
    listed = client.get("/api/transactions?month=2026-11").json()
    assert listed[0]["merchant"] == "Bách Hóa Xanh"
    # Client cũ (không gửi merchant) sửa tiền: cửa hàng giữ nguyên.
    assert write(client, "PUT", f"/transactions/{rid}", {**EXPENSE, "amount_vnd": 130000}).json()["merchant"] == "Bách Hóa Xanh"
    # null tường minh cũng là "không đổi".
    assert write(client, "PUT", f"/transactions/{rid}", {**EXPENSE, "merchant": None}).json()["merchant"] == "Bách Hóa Xanh"
    assert write(client, "PUT", f"/transactions/{rid}", {**EXPENSE, "merchant": "WinMart"}).json()["merchant"] == "WinMart"
    # Chuỗi rỗng = xóa cửa hàng.
    assert write(client, "PUT", f"/transactions/{rid}", {**EXPENSE, "merchant": ""}).json()["merchant"] is None
    assert client.get("/api/transactions?month=2026-11").json()[0]["merchant"] is None


def test_merchant_rejections_and_blank(client):
    assert write(client, "POST", "/transactions", {**EXPENSE, "merchant": "a" * 121}).status_code == 422
    assert write(client, "POST", "/transactions", {**EXPENSE, "merchant": 7}).status_code == 422
    rid = write(client, "POST", "/transactions", {**EXPENSE, "merchant": "   "}).json()["id"]
    assert [r["merchant"] for r in client.get("/api/transactions?month=2026-11").json() if r["id"] == rid] == [None]


def test_idempotency_without_merchant_unchanged(client):
    # Yêu cầu không có cửa hàng (client Android hiện tại) phát lại cùng khóa: một bản ghi.
    first = write(client, "POST", "/transactions", EXPENSE, key="same-key-001")
    again = write(client, "POST", "/transactions", {**EXPENSE, "merchant": None}, key="same-key-001")
    assert first.json() == again.json()
    assert "merchant" not in first.json()["data"]
    assert len(client.get("/api/transactions?month=2026-11").json()) == 1
    # Cùng khóa, nội dung khác (thêm cửa hàng) bị từ chối.
    assert write(client, "POST", "/transactions", {**EXPENSE, "merchant": "X"}, key="same-key-001").status_code == 400


def test_bill_payment_expense_written_with_named_columns(client):
    bid = write(client, "POST", "/bills", {"name": "Điện", "amount_vnd": 500000, "due_day": 10, "start_month": "2026-11"}).json()["id"]
    paid = write(client, "POST", f"/bills/{bid}/pay", {"month": "2026-11", "paid_on": "2026-11-12"}).json()
    row = [r for r in client.get("/api/transactions?month=2026-11").json() if r["id"] == paid["expense_id"]][0]
    assert (row["amount_vnd"], row["category_id"], row["merchant"]) == (500000, "utilities", None)
    assert client.get("/api/bills").json()["schedules"][0]["end_month"] is None
