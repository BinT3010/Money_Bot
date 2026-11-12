"""D03 (F2): số tiền thực trả khi xác nhận thanh toán một kỳ hóa đơn."""
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app

TODAY = date(2026, 11, 12)


@pytest.fixture
def c(tmp_path):
    client = TestClient(create_app(tmp_path, lambda: TODAY))
    token = client.post("/api/auth/register", json={"email": "p@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def write(c, method, path, body, key=None):
    return c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": key or uuid4().hex})


def bill(c, amount=500000, start="2026-10"):
    return write(c, "POST", "/bills", {"name": "Điện", "amount_vnd": amount, "due_day": 20, "start_month": start}).json()["id"]


def period(c, month):
    return [p for p in c.get("/api/bills").json()["periods"] if p["month"] == month][0]


def utilities(c, amount, day="2026-11-12"):
    body = {"amount_vnd": amount, "description": "Tiền điện tháng 11", "category_id": "utilities", "date": day}
    return write(c, "POST", "/transactions", body).json()["id"]


def test_default_amount_is_scheduled_amount(c):
    b = bill(c)
    r = write(c, "POST", f"/bills/{b}/pay", {"month": "2026-11", "paid_on": "2026-11-12"}).json()
    assert r["paid_amount"] == 500000 and not r["already_paid"]
    p = period(c, "2026-11")
    assert (p["status"], p["amount_vnd"], p["paid_amount"], p["paid_on"]) == ("PAID", 500000, 500000, "2026-11-12")
    assert c.get("/api/summary?month=2026-11").json()["total"] == 500000


def test_actual_amount_creates_expense_with_that_amount(c):
    b = bill(c)
    r = write(c, "POST", f"/bills/{b}/pay", {"month": "2026-11", "paid_on": "2026-11-12", "amount_vnd": 487000}).json()
    assert r["paid_amount"] == 487000
    p = period(c, "2026-11")
    assert (p["amount_vnd"], p["paid_amount"]) == (500000, 487000)  # dự kiến vẫn giữ để so sánh
    s = c.get("/api/summary?month=2026-11").json()
    assert (s["total"], s["by_category"]) == (487000, {"utilities": 487000})


@pytest.mark.parametrize("amount,status", [(0, 400), (-5, 400), (10**12 + 1, 400), (True, 422), (1.5, 422), ("480000", 422)])
def test_invalid_actual_amount_rejected_without_side_effects(c, amount, status):
    b = bill(c)
    r = write(c, "POST", f"/bills/{b}/pay", {"month": "2026-11", "paid_on": "2026-11-12", "amount_vnd": amount})
    assert r.status_code == status
    assert period(c, "2026-11")["status"] != "PAID"
    assert c.get("/api/summary?month=2026-11").json()["count"] == 0


def test_link_existing_expense_with_different_amount(c):
    b = bill(c)
    e = utilities(c, 480000)
    r = write(c, "POST", f"/bills/{b}/pay", {"month": "2026-11", "paid_on": "2026-11-12", "expense_id": e}).json()
    assert (r["expense_id"], r["paid_amount"]) == (e, 480000)
    assert period(c, "2026-11")["paid_amount"] == 480000
    # Không ghi chi lần nữa.
    assert c.get("/api/summary?month=2026-11").json()["count"] == 1


def test_link_rules(c):
    b = bill(c)
    e = utilities(c, 480000)
    base = {"month": "2026-11", "paid_on": "2026-11-12", "expense_id": e}
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "amount_vnd": 500000}).status_code == 400  # khác tiền giao dịch
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "paid_on": "2026-11-11"}).status_code == 400  # khác ngày
    food = write(c, "POST", "/transactions", {"amount_vnd": 480000, "description": "x", "category_id": "food", "date": "2026-11-12"}).json()["id"]
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "expense_id": food}).status_code == 400  # sai danh mục
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "expense_id": "nope"}).status_code == 400
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "amount_vnd": 480000}).status_code == 200  # khớp thì được
    assert write(c, "POST", f"/bills/{b}/pay", {**base, "month": "2026-10"}).status_code == 400  # đã liên kết kỳ khác


def test_confirm_twice_and_replay(c):
    b = bill(c)
    body = {"month": "2026-11", "paid_on": "2026-11-12", "amount_vnd": 487000}
    first = write(c, "POST", f"/bills/{b}/pay", body, key="pay-key-0001").json()
    assert write(c, "POST", f"/bills/{b}/pay", body, key="pay-key-0001").json() == first  # cùng khóa
    second = write(c, "POST", f"/bills/{b}/pay", {**body, "amount_vnd": 999000}).json()  # khóa mới, kỳ đã trả
    assert second["already_paid"] and second["paid_amount"] == 487000
    assert c.get("/api/summary?month=2026-11").json()["count"] == 1
    # Cùng khóa nhưng đổi số tiền: từ chối.
    assert write(c, "POST", f"/bills/{b}/pay", {**body, "amount_vnd": 1}, key="pay-key-0001").status_code == 400


def test_legacy_client_payload_replays_identically(c):
    # Client Android hiện tại gửi expense_id null và không có amount_vnd.
    b = bill(c)
    body = {"month": "2026-11", "paid_on": "2026-11-12", "expense_id": None}
    first = write(c, "POST", f"/bills/{b}/pay", body, key="legacy-key-01").json()
    again = write(c, "POST", f"/bills/{b}/pay", {**body, "amount_vnd": None}, key="legacy-key-01").json()
    assert first == again and first["paid_amount"] == 500000
