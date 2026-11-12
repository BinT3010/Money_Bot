"""
test_bill_reminder.py - Bộ kiểm thử tự động cho smart_money_bot_bill_reminder.py
Chạy: python -m unittest test_bill_reminder.py -v
"""

import unittest
from datetime import date
from smart_money_bot_bill_reminder import (
    tach_lich_hoa_don,
    kiem_tra_hoa_don,
    ngay_han_thuc_te,
    kiem_tra_nhac_han,
    due_bills,
    xac_nhan_thanh_toan,
)

TODAY = date(2026, 11, 12)


class TestBillReminder(unittest.TestCase):
    # K01: Tách câu tạo lịch hóa đơn định kỳ
    def test_T01_parse_full_sentence_from_app(self):
        r = tach_lich_hoa_don("Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026", today=TODAY)
        self.assertEqual(r["name"], "Tiền mạng")
        self.assertEqual(r["amount_vnd"], 200000)
        self.assertEqual(r["due_day"], 5)
        self.assertEqual(r["start_month"], "2026-12")
        self.assertEqual(r["trang_thai"], "cho_xac_nhan")

    def test_T02_parse_default_start_month(self):
        r = tach_lich_hoa_don("Tiền điện 450k hạn ngày 20 hàng tháng", today=TODAY)
        self.assertEqual(r["start_month"], "2026-11")
        self.assertTrue(r["start_month_mac_dinh"])
        self.assertEqual(r["trang_thai"], "cho_xac_nhan")

    def test_T03_missing_amount_requires_supplement(self):
        r = tach_lich_hoa_don("Tiền phòng hạn ngày 5 mỗi tháng", today=TODAY)
        self.assertIn("amount_vnd", r["truong_thieu"])
        self.assertEqual(r["trang_thai"], "can_bo_sung")

    def test_T04_missing_due_day_requires_supplement(self):
        r = tach_lich_hoa_don("Tiền nước 100k mỗi tháng", today=TODAY)
        self.assertIn("due_day", r["truong_thieu"])
        self.assertEqual(r["trang_thai"], "can_bo_sung")

    def test_T05_validate_rejects_bool_and_negative_amount(self):
        r = {"name": "Internet", "amount_vnd": True, "due_day": 10, "start_month": "2026-11"}
        kiem_tra_hoa_don(r)
        self.assertIn("amount_vnd", r["truong_thieu"])
        r["amount_vnd"] = -50000
        kiem_tra_hoa_don(r)
        self.assertIn("amount_vnd", r["truong_thieu"])

    def test_T06_validate_rejects_invalid_due_day_and_month(self):
        r = {"name": "Internet", "amount_vnd": 200000, "due_day": 32, "start_month": "2026-13"}
        kiem_tra_hoa_don(r)
        self.assertIn("due_day", r["truong_thieu"])
        self.assertIn("start_month", r["truong_thieu"])

    # K02: Tính ngày đến hạn thực tế cho tháng ngắn & năm nhuận
    def test_T07_due_day_31_in_30_day_month_and_recovery(self):
        self.assertEqual(ngay_han_thuc_te(2026, 11, 31), date(2026, 11, 30))
        self.assertEqual(ngay_han_thuc_te(2026, 12, 31), date(2026, 12, 31))

    def test_T08_due_day_31_in_february_normal_and_leap_year(self):
        self.assertEqual(ngay_han_thuc_te(2026, 2, 31), date(2026, 2, 28))
        self.assertEqual(ngay_han_thuc_te(2028, 2, 31), date(2028, 2, 29))

    # K03: Trạng thái nhắc hạn (3 ngày, 1 ngày, hôm nay, quá hạn, đã trả, chưa bắt đầu)
    def test_T09_reminder_3_days_before(self):
        bill = {"name": "Tiền nước", "amount_vnd": 100000, "due_day": 15, "start_month": "2026-11"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "nhac_truoc_3_ngay")
        self.assertTrue(info["can_nhac"])

    def test_T10_reminder_1_day_before(self):
        bill = {"name": "Tiền điện", "amount_vnd": 450000, "due_day": 13, "start_month": "2026-11"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "nhac_truoc_1_ngay")
        self.assertTrue(info["can_nhac"])

    def test_T11_reminder_due_today(self):
        bill = {"name": "Tiền trọ", "amount_vnd": 2000000, "due_day": 12, "start_month": "2026-11"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "den_han_hom_nay")
        self.assertTrue(info["can_nhac"])

    def test_T12_reminder_overdue(self):
        bill = {"name": "Internet", "amount_vnd": 220000, "due_day": 10, "start_month": "2026-11"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "qua_han")
        self.assertEqual(info["days_left"], -2)
        self.assertTrue(info["can_nhac"])

    def test_T13_upcoming_2_days_no_push_alert(self):
        bill = {"name": "Gói 4G", "amount_vnd": 90000, "due_day": 14, "start_month": "2026-11"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "sap_den_han")
        self.assertFalse(info["can_nhac"])

    def test_T14_paid_bill_stops_reminder(self):
        bill = {"name": "Tiền điện", "amount_vnd": 450000, "due_day": 10, "paid_months": ["2026-11"]}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "da_thanh_toan")
        self.assertFalse(info["can_nhac"])

    def test_T15_future_start_month_not_active_yet(self):
        bill = {"name": "Tiền mạng", "amount_vnd": 200000, "due_day": 5, "start_month": "2026-12"}
        info = kiem_tra_nhac_han(bill, today=date(2026, 11, 12))
        self.assertEqual(info["status"], "chua_bat_dau")
        self.assertFalse(info["can_nhac"])

    # K04: Tổng hợp due_bills và xác nhận thanh toán chống ghi trùng
    def test_T16_due_bills_aggregates_and_skips_future_bills(self):
        bills = [
            {"name": "Internet", "amount_vnd": 220000, "due_day": 10, "start_month": "2026-11"},
            {"name": "Tiền điện", "amount_vnd": 450000, "due_day": 13, "start_month": "2026-11"},
            {"name": "Tiền phòng", "amount_vnd": 2000000, "due_day": 5, "start_month": "2026-11", "da_tra": True},
            {"name": "Cáp quang mới", "amount_vnd": 300000, "due_day": 5, "start_month": "2026-12"},
        ]
        res = due_bills(bills, today=date(2026, 11, 12))
        self.assertEqual(len(res["items"]), 3)
        self.assertEqual(len(res["reminders"]), 2)
        self.assertEqual(res["total_due_vnd"], 670000)

    def test_T17_confirm_payment_creates_single_expense_and_blocks_duplicate(self):
        bill = {"name": "Tiền điện", "amount_vnd": 450000, "due_day": 20, "start_month": "2026-11"}
        ok1, tx1, _ = xac_nhan_thanh_toan(bill, today=TODAY)
        ok2, tx2, _ = xac_nhan_thanh_toan(bill, today=TODAY)
        self.assertTrue(ok1)
        self.assertEqual(tx1["so_tien"], 450000)
        self.assertEqual(tx1["danh_muc"], "Hóa đơn tiện ích")
        self.assertFalse(ok2)
        self.assertIsNone(tx2)


if __name__ == "__main__":
    unittest.main()