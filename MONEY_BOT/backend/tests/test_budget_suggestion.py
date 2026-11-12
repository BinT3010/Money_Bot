"""D06 (F4): gợi ý hạn mức kèm căn cứ (lịch sử, trung bình, A, B, hệ số, phần chưa phân bổ)."""
import random
import time
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app

TODAY = date(2026, 11, 12)
ORDER = ["food", "shopping", "transport", "utilities", "entertainment", "other"]


@pytest.fixture
def c(tmp_path):
    client = TestClient(create_app(tmp_path, lambda: TODAY))
    token = client.post("/api/auth/register", json={"email": "g@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def write(c, method, path, body):
    r = c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 200, r.text
    return r.json()


def spend(c, month, amounts):
    for cid, amount in zip(ORDER, amounts):
        if amount:
            write(c, "POST", "/transactions", {"amount_vnd": amount, "description": "chi", "category_id": cid, "date": f"{month}-05"})


def budget(c, total):
    write(c, "PUT", "/budgets/2026-11", {"total": total, "limits": {}})


def suggest(c):
    return c.get("/api/budgets/2026-11/suggestions").json()


SPEC = [2000000, 1000000, 500000, 1000000, 300000, 200000]


def test_spec_example_factor_0_8(c):
    for m in ("2026-08", "2026-09", "2026-10"):
        spend(c, m, SPEC)
    budget(c, 4000000)
    s = suggest(c)
    assert s["available"] and s["months"] == ["2026-08", "2026-09", "2026-10"]
    assert [s["limits"][k] for k in ORDER] == [1600000, 800000, 400000, 800000, 240000, 160000]
    assert [s["averages"][k] for k in ORDER] == SPEC
    assert (s["average_total"], s["budget_total"], s["factor"]) == (5000000, 4000000, 0.8)
    assert (s["allocated"], s["unallocated"]) == (4000000, 0)
    assert s["history"]["2026-09"]["food"] == 2000000 and s["totals"]["food"] == 6000000
    assert "B/A = 0,8" in s["reason"] and "5.000.000đ" in s["reason"] and "Phần chưa phân bổ: 0đ" in s["reason"]


def test_budget_above_average_keeps_averages(c):
    for m in ("2026-08", "2026-09", "2026-10"):
        spend(c, m, SPEC)
    budget(c, 6000000)
    s = suggest(c)
    assert [s["limits"][k] for k in ORDER] == SPEC
    assert (s["factor"], s["allocated"], s["unallocated"]) == (1.0, 5000000, 1000000)
    assert "hệ số 1" in s["reason"] and "Phần chưa phân bổ: 1.000.000đ" in s["reason"]


def test_fractional_average_floors_and_zero_months_count(c):
    spend(c, "2026-08", [100000, 0, 0, 0, 0, 0])   # chỉ chi tháng 8: tháng 9, 10 tính 0
    spend(c, "2026-10", [0, 50000, 0, 0, 0, 0])
    budget(c, 1000000)
    s = suggest(c)
    assert (s["averages"]["food"], s["limits"]["food"]) == (33333.33, 33333)
    assert (s["averages"]["shopping"], s["limits"]["shopping"]) == (16666.67, 16666)
    assert s["history"]["2026-09"] == {k: 0 for k in ORDER}
    assert "33.333,33đ" not in s["reason"] and "50.000đ" in s["reason"]  # A = 150.000/3 = 50.000


def test_missing_inputs_reported(c):
    assert suggest(c)["missing"] == ["budget", "history"]
    budget(c, 1000000)
    assert suggest(c)["missing"] == ["history"]
    spend(c, "2026-09", [10000, 0, 0, 0, 0, 0])   # lịch sử chưa đủ ba tháng
    s = suggest(c)
    assert not s["available"] and s["missing"] == ["history"]


def test_three_months_without_spending(c):
    spend(c, "2026-07", [10000, 0, 0, 0, 0, 0])   # lịch sử bắt đầu trước, nhưng ba tháng xét không chi
    budget(c, 1000000)
    s = suggest(c)
    assert not s["available"] and s["missing"] == [] and "Ba tháng không có chi tiêu" in s["reason"]


@pytest.mark.parametrize("seed", range(5))
def test_total_never_exceeds_budget_and_matches_formula(c, seed):
    rng = random.Random(seed)
    data = {m: [rng.choice([0, rng.randint(1, 3000) * 1000]) for _ in ORDER] for m in ("2026-08", "2026-09", "2026-10")}
    data["2026-08"][0] = max(data["2026-08"][0], 1000)
    for m, amounts in data.items():
        spend(c, m, amounts)
    total = rng.randint(1, 8000) * 1000
    budget(c, total)
    started = time.perf_counter()
    s = suggest(c)
    assert time.perf_counter() - started <= 5
    sums = [sum(data[m][i] for m in data) for i in range(6)]
    a = sum(sums) / 3
    for i, k in enumerate(ORDER):
        # Tính lại độc lập bằng phân số để tránh sai số float.
        from fractions import Fraction
        expected = int(Fraction(sums[i], 3) * min(Fraction(1), Fraction(total) / Fraction(sum(sums), 3)))
        assert s["limits"][k] == expected
    assert sum(s["limits"].values()) <= total and s["unallocated"] == total - sum(s["limits"].values())
    assert s["factor"] == pytest.approx(min(1, total / a), abs=1e-6)
