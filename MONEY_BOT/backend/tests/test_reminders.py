"""Nhắc hạn giáp ranh tháng/năm: kỳ tháng sau có hạn trong 3 ngày tới phải được nhắc."""
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app


class Clock:
    value = date(2026, 11, 28)


@pytest.fixture
def c(tmp_path):
    clock = Clock()
    client = TestClient(create_app(tmp_path, lambda: clock.value))
    token = client.post("/api/auth/register", json={"email": "r@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    client.clock = clock
    return client


def bill(c, due_day, start):
    r = c.post("/api/bills", json={"name": "Nước", "amount_vnd": 120000, "due_day": due_day, "start_month": start},
               headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 200
    return r.json()["id"]


def reminders(c):
    return [(n["month"], n["due_date"], n["days"]) for n in c.get("/api/bills").json()["reminders"]]


@pytest.mark.parametrize("today,expected", [
    (date(2026, 11, 28), [("2026-12", "2026-12-01", 3)]),
    (date(2026, 11, 29), []),
    (date(2026, 11, 30), [("2026-12", "2026-12-01", 1)]),
    (date(2026, 12, 1), [("2026-12", "2026-12-01", 0)]),
])
def test_next_month_due_day_1(c, today, expected):
    bill(c, 1, "2026-11")
    c.clock.value = today
    # Kỳ tháng 11 (hạn 01/11) đã quá hạn nên không thuộc mốc nhắc; chỉ còn kỳ tháng 12.
    assert reminders(c) == expected


def test_year_boundary_and_future_start(c):
    c.clock.value = date(2026, 12, 29)
    bill(c, 1, "2027-01")   # lịch bắt đầu tháng sau
    assert reminders(c) == [("2027-01", "2027-01-01", 3)]
    # Danh sách kỳ và đối chiếu vẫn chỉ đến tháng hiện tại.
    data = c.get("/api/bills").json()
    assert data["periods"] == [] and data["obligations"]["items"] == []


def test_stopped_schedule_not_reminded_next_month(c):
    b = bill(c, 1, "2026-10")
    c.post(f"/api/bills/{b}/pay", json={"month": "2026-11", "paid_on": "2026-11-28"}, headers={"Idempotency-Key": uuid4().hex})
    c.delete(f"/api/bills/{b}", headers={"Idempotency-Key": uuid4().hex})   # kết thúc ở tháng 11
    assert reminders(c) == []
