"""D04 (F3): sửa lịch hóa đơn (PUT) và ngừng/xóa lịch (DELETE, end_month)."""
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app

TODAY = date(2026, 11, 12)
BILL = {"name": "Internet", "amount_vnd": 300000, "due_day": 20, "start_month": "2026-09"}


@pytest.fixture
def c(tmp_path):
    client = TestClient(create_app(tmp_path, lambda: TODAY))
    token = client.post("/api/auth/register", json={"email": "s@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def write(c, method, path, body=None, key=None):
    return c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": key or uuid4().hex})


def create(c, **kw):
    return write(c, "POST", "/bills", {**BILL, **kw}).json()["id"]


def periods(c):
    return {p["month"]: p for p in c.get("/api/bills").json()["periods"]}


def pay(c, b, month, paid_on="2026-11-12", **kw):
    return write(c, "POST", f"/bills/{b}/pay", {"month": month, "paid_on": paid_on, **kw})


def test_edit_due_day_recomputes_unpaid_periods_and_reminders(c):
    b = create(c)
    assert pay(c, b, "2026-09").status_code == 200
    r = write(c, "PUT", f"/bills/{b}", {**BILL, "due_day": 15})
    assert r.status_code == 200 and r.json()["due_day"] == 15
    p = periods(c)
    assert [p[m]["due_date"] for m in ("2026-09", "2026-10", "2026-11")] == ["2026-09-15", "2026-10-15", "2026-11-15"]
    assert p["2026-09"]["status"] == "PAID"
    reminders = c.get("/api/bills").json()["reminders"]
    assert [(n["month"], n["days"]) for n in reminders] == [("2026-11", 3)]


def test_edit_amount_keeps_paid_amount_history(c):
    b = create(c)
    pay(c, b, "2026-10")
    write(c, "PUT", f"/bills/{b}", {**BILL, "amount_vnd": 350000})
    p = periods(c)
    assert (p["2026-10"]["paid_amount"], p["2026-11"]["amount_vnd"], p["2026-11"]["paid_amount"]) == (300000, 350000, None)
    assert c.get("/api/summary?month=2026-11").json()["total"] == 300000


@pytest.mark.parametrize("change,status", [
    ({"name": " "}, 400), ({"amount_vnd": 0}, 400), ({"due_day": 32}, 400), ({"start_month": "2026-9"}, 400),
    ({"end_month": "2026-08"}, 400), ({"end_month": "2026-13"}, 400), ({"extra": 1}, 422), ({"amount_vnd": "1"}, 422),
])
def test_edit_validation(c, change, status):
    b = create(c)
    assert write(c, "PUT", f"/bills/{b}", {**BILL, **change}).status_code == status
    assert c.get("/api/bills").json()["schedules"][0]["due_day"] == 20


def test_edit_cannot_drop_paid_periods(c):
    b = create(c)
    pay(c, b, "2026-09")
    pay(c, b, "2026-10")
    assert write(c, "PUT", f"/bills/{b}", {**BILL, "start_month": "2026-10"}).status_code == 400
    assert write(c, "PUT", f"/bills/{b}", {**BILL, "end_month": "2026-09"}).status_code == 400
    assert write(c, "PUT", f"/bills/{b}", {**BILL, "start_month": "2026-08"}).status_code == 200  # mở rộng thì được
    assert write(c, "PUT", "/bills/unknown", BILL).status_code == 400


def test_delete_without_payments_removes_schedule(c):
    b = create(c)
    r = write(c, "DELETE", f"/bills/{b}").json()
    assert r == {"id": b, "deleted": True, "end_month": None}
    data = c.get("/api/bills").json()
    assert data["schedules"] == [] and data["periods"] == [] and data["reminders"] == []
    assert write(c, "DELETE", f"/bills/{b}").status_code == 400


def test_stop_with_history_keeps_paid_and_debt_but_no_new_periods(c):
    b = create(c)
    pay(c, b, "2026-09")
    r = write(c, "DELETE", f"/bills/{b}").json()
    # Kỳ tháng 11 chưa trả -> kết thúc ở tháng 10; nợ tháng 10 vẫn hiển thị.
    assert r == {"id": b, "deleted": False, "end_month": "2026-10"}
    p = periods(c)
    assert sorted(p) == ["2026-09", "2026-10"] and p["2026-10"]["status"] == "OVERDUE"
    assert c.get("/api/bills").json()["obligations"]["older_unpaid"][0]["month"] == "2026-10"
    assert pay(c, b, "2026-11").status_code == 400  # sau tháng kết thúc
    assert pay(c, b, "2026-10").status_code == 200  # vẫn trả được nợ cũ


def test_stop_when_current_period_paid_ends_this_month(c):
    b = create(c)
    pay(c, b, "2026-11")
    assert write(c, "DELETE", f"/bills/{b}").json()["end_month"] == "2026-11"
    assert sorted(periods(c)) == ["2026-09", "2026-10", "2026-11"]


def test_stop_explicit_end_month_rules(c):
    b = create(c)
    pay(c, b, "2026-10")
    assert write(c, "DELETE", f"/bills/{b}?end_month=2026-09").status_code == 400  # trước kỳ đã trả
    assert write(c, "DELETE", f"/bills/{b}?end_month=2026-1").status_code == 400
    assert write(c, "DELETE", f"/bills/{b}?end_month=2027-03").json()["end_month"] == "2027-03"


def test_resume_and_replay(c):
    b = create(c)
    pay(c, b, "2026-09")
    first = write(c, "DELETE", f"/bills/{b}", key="stop-key-0001").json()
    assert write(c, "DELETE", f"/bills/{b}", key="stop-key-0001").json() == first
    assert write(c, "PUT", f"/bills/{b}", {**BILL, "end_month": None}).json()["end_month"] is None
    assert sorted(periods(c)) == ["2026-09", "2026-10", "2026-11"]


def test_chat_due_list_skips_stopped_schedule(c):
    b = create(c, name="Truyền hình", due_day=14)
    pay(c, b, "2026-09")
    say = lambda text: write(c, "POST", "/chat/s1", {"action": "send", "text": text}).json()["message"]
    assert "Truyền hình" in say("xem hóa đơn đến hạn")
    write(c, "DELETE", f"/bills/{b}?end_month=2026-10")
    message = say("xem hóa đơn đến hạn")
    assert "2026-11-14" not in message and "2026-10-14" in message  # nợ tháng 10 vẫn nhắc, kỳ 11 không còn
