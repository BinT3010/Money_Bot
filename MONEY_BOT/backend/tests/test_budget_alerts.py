"""D07 (Ou2/Ou4): câu cảnh báo hạn mức theo danh mục và dữ liệu biểu đồ chi theo tháng."""
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app

TODAY = date(2026, 11, 12)


@pytest.fixture
def c(tmp_path):
    client = TestClient(create_app(tmp_path, lambda: TODAY))
    token = client.post("/api/auth/register", json={"email": "a@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def write(c, method, path, body=None):
    r = c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 200, r.text
    return r.json()


def spend(c, amount, day, category="food"):
    return write(c, "POST", "/transactions", {"amount_vnd": amount, "description": "chi", "category_id": category, "date": day})["id"]


def row(c, month, cid="food"):
    return [r for r in c.get(f"/api/summary?month={month}").json()["categories"] if r["category_id"] == cid][0]


def test_spec_highlands_example(c):
    write(c, "PUT", "/budgets/2026-10", {"total": 6000000, "limits": {"food": 2000000}})
    spend(c, 1540000, "2026-10-05")
    spend(c, 65000, "2026-10-13")  # Highlands Coffee
    r = row(c, "2026-10")
    assert (r["spent"], r["limit"], r["level"], r["percent"], r["remaining"]) == (1605000, 2000000, "WARNING", 80.25, 395000)
    assert r["message"] == "Ăn uống đã dùng 1.605.000/2.000.000đ (80,25%), còn 395.000đ trong hạn mức tháng 10."


def test_reached_over_and_recompute_after_edit_delete(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {"food": 100000}})
    first = spend(c, 100000, "2026-11-01")
    r = row(c, "2026-11")
    assert (r["level"], r["percent"]) == ("REACHED", 100.0)
    assert r["message"] == "Ăn uống đã dùng 100.000/100.000đ (100%), còn 0đ trong hạn mức tháng 11."
    second = spend(c, 20000, "2026-11-02")
    r = row(c, "2026-11")
    assert (r["level"], r["remaining"]) == ("OVER", -20000)
    assert r["message"] == "Ăn uống đã dùng 120.000/100.000đ (120%), vượt 20.000đ so với hạn mức tháng 11."
    write(c, "PUT", f"/transactions/{first}", {"amount_vnd": 60000, "description": "chi", "category_id": "food", "date": "2026-11-01"})
    assert row(c, "2026-11")["level"] == "WARNING"  # 80%
    write(c, "DELETE", f"/transactions/{second}")
    r = row(c, "2026-11")
    assert (r["level"], r["percent"]) == ("NORMAL", 60.0)


def test_zero_limit_and_unset(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {"entertainment": 0, "other": 0}})
    spend(c, 50000, "2026-11-03", "entertainment")
    ent, other, food = row(c, "2026-11", "entertainment"), row(c, "2026-11", "other"), row(c, "2026-11", "food")
    assert (ent["level"], ent["percent"], ent["message"]) == ("OVER", None, "Giải trí: hạn mức 0đ nhưng đã chi 50.000đ trong tháng 11, vượt hạn mức.")
    assert (other["level"], other["message"]) == ("NORMAL", "Khác: hạn mức 0đ, chưa chi trong tháng 11.")
    assert (food["level"], food["message"], food["remaining"]) == ("UNSET", None, None)


def test_threshold_change_and_rounding(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {"food": 300000}, "threshold": 80})
    spend(c, 100000, "2026-11-01")
    assert (row(c, "2026-11")["percent"], row(c, "2026-11")["level"]) == (33.33, "NORMAL")
    spend(c, 100000, "2026-11-02")
    r = row(c, "2026-11")
    assert (r["percent"], r["level"]) == (66.67, "NORMAL") and "(66,67%)" in r["message"]
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {"food": 300000}, "threshold": 60})
    assert row(c, "2026-11")["level"] == "WARNING"


def test_history_for_monthly_chart(c):
    spend(c, 100000, "2026-09-10")
    spend(c, 30000, "2026-11-01", "transport")
    h = c.get("/api/summary/history?month=2026-11&months=3").json()
    assert [m["month"] for m in h] == ["2026-09", "2026-10", "2026-11"]
    assert [m["total"] for m in h] == [100000, 0, 30000]
    assert h[1]["by_category"] == {k: 0 for k in ["food", "shopping", "transport", "utilities", "entertainment", "other"]}
    assert h[2]["by_category"]["transport"] == 30000 and h[2]["count"] == 1
    assert len(c.get("/api/summary/history?month=2026-11").json()) == 6
    for bad in ("months=0", "months=25", "months=x"):
        assert c.get(f"/api/summary/history?month=2026-11&{bad}").status_code in (400, 422)
    assert c.get("/api/summary/history?month=2026-13").status_code == 400
