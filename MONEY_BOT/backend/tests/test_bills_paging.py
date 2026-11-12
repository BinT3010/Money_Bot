"""D08: danh sách kỳ hóa đơn có giới hạn (lịch từ năm 2000 sinh 323 kỳ)."""
import json
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app

TODAY = date(2026, 11, 12)


@pytest.fixture
def c(tmp_path):
    client = TestClient(create_app(tmp_path, lambda: TODAY))
    token = client.post("/api/auth/register", json={"email": "pg@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def write(c, method, path, body):
    r = c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 200, r.text
    return r.json()


def test_long_schedule_is_paged(c):
    b = write(c, "POST", "/bills", {"name": "Cũ", "amount_vnd": 1000, "due_day": 1, "start_month": "2000-01"})["id"]
    r = c.get("/api/bills")
    data = r.json()
    assert (data["periods_total"], len(data["periods"]), data["has_more"], data["offset"], data["limit"]) == (323, 100, True, 0, 100)
    assert len(r.content) < 100_000  # trước đây ~180 KB
    o = data["obligations"]
    assert (len(o["older_unpaid"]), o["older_unpaid_count"], o["older_unpaid_total"]) == (50, 322, 322000)
    assert o["older_unpaid"][-1]["month"] == "2026-10"  # 50 kỳ gần nhất
    last = c.get("/api/bills?offset=300&limit=100").json()
    assert (len(last["periods"]), last["has_more"]) == (23, False)
    assert last["periods"][-1]["month"] == "2026-11"
    # Ghép các trang = toàn bộ danh sách, không trùng.
    pages = [c.get(f"/api/bills?offset={o}&limit=100").json()["periods"] for o in (0, 100, 200, 300)]
    assert len({p["period_id"] for page in pages for p in page}) == 323
    assert b


def test_paid_history_filter(c):
    b = write(c, "POST", "/bills", {"name": "Điện", "amount_vnd": 1000, "due_day": 1, "start_month": "2024-01"})["id"]
    write(c, "POST", f"/bills/{b}/pay", {"month": "2024-01", "paid_on": "2024-01-15"})   # đã trả, cũ hơn 12 tháng
    write(c, "POST", f"/bills/{b}/pay", {"month": "2026-01", "paid_on": "2026-01-15"})   # đã trả, trong 12 tháng
    recent = c.get("/api/bills").json()
    months = {p["month"]: p["status"] for p in recent["periods"]}
    assert "2024-01" not in months and months["2026-01"] == "PAID" and months["2024-02"] == "OVERDUE"
    assert recent["periods_total"] == 35 - 1
    full = c.get("/api/bills?history=all").json()
    assert full["periods_total"] == 35 and {p["month"]: p["status"] for p in full["periods"]}["2024-01"] == "PAID"


@pytest.mark.parametrize("query,status", [("limit=0", 400), ("limit=501", 400), ("offset=-1", 400),
                                          ("history=old", 422), ("limit=abc", 422)])
def test_paging_validation(c, query, status):
    assert c.get(f"/api/bills?{query}").status_code == status


def test_small_schedule_unchanged_for_current_client(c):
    write(c, "POST", "/bills", {"name": "Internet", "amount_vnd": 200000, "due_day": 15, "start_month": "2026-09"})
    data = c.get("/api/bills").json()
    assert [p["month"] for p in data["periods"]] == ["2026-09", "2026-10", "2026-11"]
    assert data["has_more"] is False and json.dumps(data)
