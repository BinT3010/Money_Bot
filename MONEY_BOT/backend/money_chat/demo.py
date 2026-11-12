"""D01: dữ liệu giả lập T12/T17; chỉ thêm khi người dùng yêu cầu."""
from datetime import date


def seed_demo(store):
    today = date(2026, 11, 10)
    for key, day, amount, description, category in [
        ("breakfast", "2026-11-01", 30000, "Ăn sáng", "food"),
        ("lunch", "2026-11-05", 45000, "Ăn trưa", "food"),
        ("bus", "2026-11-07", 20000, "Xe buýt", "transport"),
    ]:
        store.commit("demo-v1:" + key, "record_expense", dict(date=day, amount_vnd=amount, description=description, category_id=category), today)
    for key, name, amount, day in [("rent", "Tiền phòng", 2000000, 8), ("net", "Internet", 200000, 13), ("electric", "Điện", 300000, 12)]:
        receipt = store.commit("demo-v1:" + key, "create_recurring_bill", dict(name=name, amount_vnd=amount, due_day=day, start_month="2026-11"), today)
        if key == "electric":
            # Fixture trạng thái, không phải nghiệp vụ xác nhận thanh toán của app.
            store.seed_paid_period(receipt["id"] + ":2026-11")
