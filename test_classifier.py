"""
test_classifier.py - Bộ test mẫu cho classifier.py

Chạy:  python test_classifier.py        (in bảng kết quả + độ chính xác)
hoặc:  pytest test_classifier.py        (nếu có pytest)

Lưu ý: đây là bộ tự viết để kiểm tra nhanh. Bộ đánh giá cuối theo báo cáo
(>= 20 câu nhập, mỗi danh mục >= 2 câu) nên được viết riêng và không dùng
để chỉnh từ khóa.
"""

from datetime import date

from classifier import classify, kiem_tra_ban_ghi

TODAY = date(2026, 11, 11)

# (câu nhập, số tiền đúng, danh mục đúng; None = phải để người dùng chọn / thiếu)
CASES = [
    # Ăn uống
    ("ăn trưa 45k", 45000, "Ăn uống"),
    ("cà phê 30k", 30000, "Ăn uống"),
    ("trà sữa 55.000đ", 55000, "Ăn uống"),
    ("an toi 120 nghin", 120000, "Ăn uống"),          # không dấu
    ("đặt đồ ăn 150k", 150000, "Ăn uống"),
    # Tiêu dùng/Mua sắm
    ("mua áo 250k", 250000, "Tiêu dùng/Mua sắm"),
    ("siêu thị 320.000", 320000, "Tiêu dùng/Mua sắm"),
    ("mua dầu gội 89k", 89000, "Tiêu dùng/Mua sắm"),
    # Đi lại
    ("đổ xăng 70k", 70000, "Đi lại"),
    ("grab đi học 35k", 35000, "Đi lại"),
    ("gửi xe 5k", 5000, "Đi lại"),
    # Hóa đơn tiện ích
    ("tiền điện 850.000đ", 850000, "Hóa đơn tiện ích"),
    ("tiền nước 120k", 120000, "Hóa đơn tiện ích"),   # "nước" không bị nhầm sang Ăn uống
    ("tiền phòng 2tr", 2000000, "Hóa đơn tiện ích"),
    ("internet 1tr5", 1500000, "Hóa đơn tiện ích"),
    ("cước điện thoại 100k", 100000, "Hóa đơn tiện ích"),
    # Giải trí
    ("xem phim 90k", 90000, "Giải trí"),
    ("netflix 260k", 260000, "Giải trí"),
    ("karaoke 200k", 200000, "Giải trí"),
    # Khác
    ("mua thuốc 60k", 60000, "Khác"),
    ("photo tài liệu 15k", 15000, "Khác"),
    ("mua vở bút 40k", 40000, "Khác"),
    # Cách viết số tiền khác nhau
    ("cơm tấm 1.200.000 đồng", 1200000, "Ăn uống"),
    ("lẩu 2 triệu rưỡi", 2500000, "Ăn uống"),
    ("bún bò 45000", 45000, "Ăn uống"),
    # Thiếu / không đủ căn cứ -> không đoán, không lưu
    ("ăn trưa", None, "Ăn uống"),                       # thiếu tiền
    ("mua đồ Bách Hóa Xanh", None, None),               # chỉ có tên cửa hàng
    ("100k", 100000, None),                             # thiếu nội dung và danh mục
    ("cà phê và xem phim 100k", 100000, None),          # hòa điểm hai danh mục
    ("ăn trưa 45", None, "Ăn uống"),                    # số 45 chưa rõ đơn vị
]


def run_all():
    rows = []
    for text, want_amount, want_cat in CASES:
        r = classify(text, today=TODAY)
        rows.append((text, want_amount, want_cat, r))
    return rows


def test_amount_and_category():
    for text, want_amount, want_cat, r in run_all():
        assert r["so_tien"] == want_amount, f"{text!r}: số tiền {r['so_tien']} != {want_amount}"
        assert r["danh_muc"] == want_cat, f"{text!r}: danh mục {r['danh_muc']} != {want_cat}"


def test_missing_fields_not_ready_to_save():
    r = classify("ăn trưa", today=TODAY)
    assert "so_tien" in r["truong_thieu"] and r["trang_thai"] == "can_bo_sung"
    r = classify("mua đồ Bách Hóa Xanh", today=TODAY)
    assert "danh_muc" in r["truong_thieu"] and r["trang_thai"] == "can_bo_sung"


def test_default_and_explicit_date():
    r = classify("ăn trưa 45k", today=TODAY)
    assert r["ngay"] == "2026-11-11" and r["ngay_mac_dinh"] is True
    r = classify("ăn trưa 45k hôm qua", today=TODAY)
    assert r["ngay"] == "2026-11-10" and r["ngay_mac_dinh"] is False
    r = classify("cà phê 65k ngày 13/10/2026", today=TODAY)
    assert r["ngay"] == "2026-10-13" and r["so_tien"] == 65000   # ngày không bị đọc thành tiền
    r = classify("cà phê 65k 13/10", today=TODAY)
    assert r["ngay"] == "2026-10-13"


def test_invalid_date_warns():
    r = classify("ăn trưa 45k 31/02", today=TODAY)
    assert r["ngay"] == "2026-11-11" and r["canh_bao"]


def test_content_is_cleaned():
    r = classify("ăn trưa 45k hôm nay", today=TODAY)
    assert r["noi_dung"] == "ăn trưa"


def test_user_fixes_category_then_ready():
    r = classify("mua đồ Bách Hóa Xanh 150k", today=TODAY)
    assert r["danh_muc"] is None
    r["danh_muc"] = "Tiêu dùng/Mua sắm"          # người dùng chọn từ dropdown
    kiem_tra_ban_ghi(r)
    assert r["truong_thieu"] == [] and r["trang_thai"] == "cho_xac_nhan"


def test_invalid_amount_rejected_on_recheck():
    r = classify("ăn trưa 45k", today=TODAY)
    r["so_tien"] = -5
    kiem_tra_ban_ghi(r)
    assert "so_tien" in r["truong_thieu"]


def report():
    rows = run_all()
    ok_amount = ok_cat = 0
    per_cat = {}
    print(f"{'Câu nhập':32} {'Tiền':>10} {'Danh mục dự đoán':20} Kết quả")
    print("-" * 80)
    for text, want_amount, want_cat, r in rows:
        a_ok = r["so_tien"] == want_amount
        c_ok = r["danh_muc"] == want_cat
        ok_amount += a_ok
        ok_cat += c_ok
        stats = per_cat.setdefault(want_cat or "(phải để người dùng chọn)", [0, 0])
        stats[0] += c_ok
        stats[1] += 1
        tien = f"{r['so_tien']:,}".replace(",", ".") if r["so_tien"] else "-"
        print(f"{text:32} {tien:>10} {str(r['danh_muc']):20} {'ĐÚNG' if a_ok and c_ok else 'SAI'}")
    n = len(rows)
    print("-" * 80)
    print(f"Số tiền đúng : {ok_amount}/{n} = {ok_amount / n:.0%}")
    print(f"Danh mục đúng: {ok_cat}/{n} = {ok_cat / n:.0%}")
    print("\nTheo danh mục:")
    for cat, (ok, total) in per_cat.items():
        print(f"  {cat:32} {ok}/{total}")


if __name__ == "__main__":
    report()
