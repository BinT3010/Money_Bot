"""D05 (Ou7 + F5): đối chiếu tổng nghĩa vụ S với ngân sách còn lại R, theo ví dụ trong đặc tả Money_BOT.tex."""
from datetime import date
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import create_app


class Clock:
    value = date(2026, 11, 2)


@pytest.fixture
def c(tmp_path):
    clock = Clock()
    client = TestClient(create_app(tmp_path, lambda: clock.value))
    token = client.post("/api/auth/register", json={"email": "o@test.vn", "password": "Password123"}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    client.clock = clock
    return client


def write(c, method, path, body=None):
    r = c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 200, r.text
    return r.json()


def spend(c, amount, day, category="food"):
    return write(c, "POST", "/transactions", {"amount_vnd": amount, "description": "chi", "category_id": category, "date": day})


def bill(c, name, amount, due_day, start="2026-11"):
    return write(c, "POST", "/bills", {"name": name, "amount_vnd": amount, "due_day": due_day, "start_month": start})["id"]


def obligations(c):
    return c.get("/api/bills").json()["obligations"]


def test_spec_example_before_and_after_paying_rent(c):
    # Đặc tả: 02/11/2026, B = 4.000.000, C = 1.700.000; tiền phòng 2.000.000 hạn 05/11, Internet 400.000 hạn 06/11.
    write(c, "PUT", "/budgets/2026-11", {"total": 4000000, "limits": {}})
    spend(c, 1700000, "2026-11-01")
    rent = bill(c, "Tiền phòng", 2000000, 5)
    bill(c, "Internet", 400000, 6)
    o = obligations(c)
    assert (o["start"], o["end"]) == ("2026-11-02", "2026-11-08")
    assert [i["name"] for i in o["items"]] == ["Tiền phòng", "Internet"]
    assert (o["budget_total"], o["spent"], o["remaining_budget"], o["obligations"]) == (4000000, 1700000, 2300000, 2400000)
    assert (o["status"], o["shortfall"], o["difference"]) == ("SHORT", 100000, -100000)
    assert o["message"] == ("Theo dữ liệu đã ghi, ngân sách còn lại thấp hơn tổng hóa đơn đang xét 100.000đ. "
                            "Hãy kiểm tra các khoản chi và kế hoạch thanh toán.")
    assert [(n["name"], n["days"]) for n in c.get("/api/bills").json()["reminders"]] == [("Tiền phòng", 3)]
    # Trả tiền phòng, chưa ghi chi: tạo khoản chi 2.000.000 -> C = 3.700.000, R = 300.000, S = 400.000, vẫn thiếu 100.000.
    write(c, "POST", f"/bills/{rent}/pay", {"month": "2026-11", "paid_on": "2026-11-02"})
    o = obligations(c)
    assert (o["spent"], o["remaining_budget"], o["obligations"], o["shortfall"]) == (3700000, 300000, 400000, 100000)
    assert [i["name"] for i in o["items"]] == ["Internet"]


def test_no_budget_shows_total_but_no_conclusion(c):
    bill(c, "Điện", 850000, 5)
    o = obligations(c)
    assert (o["status"], o["obligations"], o["remaining_budget"], o["shortfall"], o["difference"]) == ("UNKNOWN", 850000, None, None, None)
    assert "Chưa kết luận đủ hay thiếu" in o["message"] and "850.000đ" in o["message"]


def test_enough_exact_and_surplus(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {}})
    spend(c, 600000, "2026-11-01")
    bill(c, "Nước", 400000, 4)
    o = obligations(c)
    assert (o["status"], o["surplus"], o["shortfall"]) == ("ENOUGH", 0, 0)
    assert o["message"].endswith("đủ cho các hóa đơn đang xét (vừa đủ).")
    write(c, "PUT", "/budgets/2026-11", {"total": 1250000, "limits": {}})
    o = obligations(c)
    assert o["surplus"] == 250000 and o["message"].endswith("còn dư 250.000đ.")


def test_many_bills_add_up_over_budget(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 1000000, "limits": {}})
    for i, amount in enumerate((300000, 300000, 300000, 200000)):
        bill(c, f"HĐ{i}", amount, 3 + i)
    o = obligations(c)
    assert (o["obligations"], o["status"], o["shortfall"]) == (1100000, "SHORT", 100000)


def test_out_of_window_and_overdue_this_month(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 5000000, "limits": {}})
    c.clock.value = date(2026, 11, 12)
    bill(c, "Quá hạn tháng này", 100000, 10)   # hạn 10/11 < hôm nay: vẫn cộng vào S
    bill(c, "Trong cửa sổ", 200000, 18)        # hạn 18/11 = hôm nay + 6
    bill(c, "Ngoài cửa sổ", 400000, 25)        # không cộng
    o = obligations(c)
    assert (o["start"], o["end"]) == ("2026-11-12", "2026-11-18")
    assert [i["name"] for i in o["items"]] == ["Quá hạn tháng này", "Trong cửa sổ"]
    assert o["obligations"] == 300000 and o["excluded"] is None


def test_next_month_and_older_debt_listed_separately(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 5000000, "limits": {}})
    bill(c, "Nợ cũ", 150000, 20, start="2026-10")   # kỳ 10 chưa trả = nợ tháng trước
    c.clock.value = date(2026, 11, 28)
    bill(c, "Đầu tháng sau", 250000, 2, start="2026-11")
    o = obligations(c)
    # Cửa sổ bị kẹp cuối tháng (28–30/11); kỳ 02/12 nằm trong 6 ngày tới nên liệt kê riêng.
    assert (o["start"], o["end"]) == ("2026-11-28", "2026-11-30")
    assert [(r["name"], r["due_date"]) for r in o["next_month"]] == [("Đầu tháng sau", "2026-12-02")]
    assert [(r["name"], r["month"]) for r in o["older_unpaid"]] == [("Nợ cũ", "2026-10")]
    # Kỳ tháng 11 của cả hai lịch: "Đầu tháng sau" hạn 02/11 và "Nợ cũ" hạn 20/11 đều quá hạn trong tháng -> cộng vào S.
    assert o["obligations"] == 400000
    assert o["excluded"] == "Chưa gộp vào phép đối chiếu: 1 hóa đơn tháng 2026-12 (250.000đ); 1 kỳ nợ tháng trước (150.000đ)."
    # Danh sách kỳ thường vẫn chỉ đến tháng hiện tại.
    assert max(p["month"] for p in c.get("/api/bills").json()["periods"]) == "2026-11"


def test_paid_bill_leaves_s_but_stays_in_c(c):
    write(c, "PUT", "/budgets/2026-11", {"total": 3000000, "limits": {}})
    b = bill(c, "Điện", 1000000, 4)
    before = obligations(c)
    write(c, "POST", f"/bills/{b}/pay", {"month": "2026-11", "paid_on": "2026-11-02", "amount_vnd": 950000})
    after = obligations(c)
    assert (before["obligations"], before["remaining_budget"]) == (1000000, 3000000)
    assert (after["obligations"], after["spent"], after["remaining_budget"]) == (0, 950000, 2050000)
    assert after["message"].startswith("Không có hóa đơn chưa trả trong cửa sổ này.")
