"""K01–K05: kiểm thử hành vi, không gọi API thật hoặc sửa database người dùng."""
import json
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from pathlib import Path
from unittest.mock import patch
from money_chat.engine import ChatEngine
from money_chat.models import validate
from money_chat.parser import RuleInterpreter
from money_chat.storage import SQLiteStore
from money_chat.demo import seed_demo
from money_chat.ai import HybridInterpreter


# K01: mỗi test có database riêng và ngày cố định, không phụ thuộc ngày máy.
class ChatTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store = SQLiteStore(Path(self.temp.name) / "test.db")
        self.today = date(2026, 11, 10)
        self.chat = ChatEngine(self.store, "test", self.today)

    def draft(self):
        return self.chat.snapshot()["draft"]

    def confirm(self):
        d = self.draft()
        return self.chat.confirm(d["id"], d["version"])

    # K02: T01–T11 là ghi, hỏi thiếu, sửa và hủy.
    def test_T01_confirm_required(self):
        self.chat.send("Ăn trưa 45k")
        self.assertEqual([], self.store.expenses())
        self.assertTrue(self.chat.snapshot()["can_confirm"])
        self.confirm()
        self.assertEqual(45000, self.store.expenses()[0]["amount_vnd"])

    def test_T02_missing_amount_multiturn(self):
        self.chat.send("Ăn trưa")
        self.assertIn("amount_vnd", self.chat.snapshot()["errors"])
        self.chat.send("45k")
        self.assertEqual("Ăn trưa", self.draft()["fields"]["description"])
        self.confirm()
        self.assertEqual(1, len(self.store.expenses()))

    def test_T03_missing_description(self):
        self.chat.send("Hôm nay chi 100k")
        self.assertIn("description", self.chat.snapshot()["errors"])
        self.chat.send("Đổ xăng")
        self.confirm()
        self.assertEqual("transport", self.store.expenses()[0]["category_id"])

    def test_T04_ambiguous_money(self):
        self.chat.send("Cà phê 45")
        self.assertIn("amount_vnd", self.chat.snapshot()["errors"])
        self.chat.send("45 nghìn")
        self.confirm()
        self.assertEqual(45000, self.store.expenses()[0]["amount_vnd"])

    def test_T05_invalid_amount_then_fix(self):
        self.chat.send("Ăn trưa -45k")
        self.assertFalse(self.chat.snapshot()["can_confirm"])
        self.chat.send("0")
        self.assertFalse(self.chat.snapshot()["can_confirm"])
        self.chat.send("45k")
        self.confirm()
        self.assertEqual(45000, self.store.expenses()[0]["amount_vnd"])

    def test_T06_relative_date_million(self):
        self.chat.send("Hôm qua mua bàn học 1,5 triệu")
        self.confirm()
        row = self.store.expenses()[0]
        self.assertEqual((1500000, "2026-11-09"), (row["amount_vnd"], row["date"]))

    def test_T07_invalid_date(self):
        self.chat.send("Ăn trưa 45k ngày 31/11/2026")
        self.assertIn("date", self.chat.snapshot()["errors"])
        self.chat.send("09/11/2026")
        self.confirm()
        self.assertEqual("2026-11-09", self.store.expenses()[0]["date"])

    def test_T08_manual_category(self):
        self.chat.send("Mua sách 80k")
        self.assertIn("category_id", self.chat.snapshot()["errors"])
        self.chat.send("danh mục Khác")
        self.assertEqual("Mua sách", self.draft()["fields"]["description"])
        self.confirm()
        self.assertEqual("other", self.store.expenses()[0]["category_id"])

    def test_T09_edit_invalidates_old_confirmation(self):
        self.chat.send("Vé xe 30k")
        old = self.draft()
        self.chat.send("Sửa thành mua xe đồ chơi 80k")
        self.chat.confirm(old["id"], old["version"])
        self.assertEqual([], self.store.expenses())
        self.confirm()
        row = self.store.expenses()[0]
        self.assertEqual((80000, "shopping"), (row["amount_vnd"], row["category_id"]))

    def test_T10_cancel(self):
        self.chat.send("Cà phê 30k")
        self.chat.send("hủy")
        self.chat.send("xác nhận lưu")
        self.assertEqual([], self.store.expenses())
        self.assertIsNone(self.draft())

    def test_T11_multiple_and_switch(self):
        seed_demo(self.store)
        self.chat.send("Ăn trưa 45k và gửi xe 5k")
        self.assertIsNone(self.draft())
        self.chat.send("Ăn trưa 45k")
        message = self.chat.send("Tháng này chi bao nhiêu?")
        self.assertIn("hủy và chuyển", message)
        self.assertIsNotNone(self.draft())
        result = self.chat.send("hủy và chuyển")
        self.assertIn("95.000đ", result)
        self.assertEqual(3, len(self.store.expenses()))

    # K03: đọc dữ liệu thật và tạo/xem lịch; đây là backend tham chiếu.
    def test_T12_summary_and_category(self):
        seed_demo(self.store)
        self.assertIn("95.000đ", self.chat.send("Tháng này tôi chi bao nhiêu?"))
        self.assertIn("75.000đ", self.chat.send("Riêng ăn uống trong tháng này?"))

    def test_T13_missing_year_empty_month(self):
        self.chat.send("Tháng 10 chi bao nhiêu?")
        self.assertIn("month", self.chat.snapshot()["errors"])
        self.assertIn("Chưa có khoản chi", self.chat.send("2026"))

    def test_T14_read_failure_not_zero(self):
        seed_demo(self.store)
        with patch.object(self.store, "summary", side_effect=OSError("disk")):
            result = self.chat.send("Tháng này chi bao nhiêu?")
        self.assertIn("Chưa lấy được", result)
        self.assertIn("95.000đ", self.chat.send("thử lại"))

    def test_T15_create_bill_not_expense(self):
        self.chat.send("Tạo tiền mạng 200k, hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026")
        self.assertTrue(self.chat.snapshot()["can_confirm"])
        self.confirm()
        self.assertEqual([], self.store.expenses())
        items = self.store.due_bills(date(2026, 12, 2))["items"]
        self.assertEqual("2026-12-05", items[0]["due_date"])

    def test_T16_bill_partial_and_day31(self):
        self.chat.send("Nhắc tiền phòng mỗi tháng")
        self.chat.send("2 triệu, ngày 32, từ tháng 2/2027")
        self.assertIn("due_day", self.chat.snapshot()["errors"])
        self.chat.send("Ngày 31")
        self.confirm()
        feb = self.store.due_bills(date(2027, 2, 25))["items"]
        self.assertEqual("2027-02-28", feb[0]["due_date"])
        march = self.store.due_bills(date(2027, 3, 28))["items"]
        self.assertEqual("2027-03-31", march[-1]["due_date"])

    def test_T17_due_and_overdue_excludes_paid(self):
        seed_demo(self.store)
        result = self.chat.send("Có khoản nào sắp phải đóng?")
        self.assertIn("Tiền phòng", result)
        self.assertIn("Internet", result)
        self.assertNotIn("Điện:", result)

    def test_T18_unknown_outside_scope_payment(self):
        self.assertIn("Mình hỗ trợ", self.chat.send("Làm cái đó giúp mình"))
        self.assertIn("Mình hỗ trợ", self.chat.send("Chọn laptop tốt nhất"))
        self.assertIn("chưa cập nhật đã trả", self.chat.send("Đánh dấu tiền mạng đã trả"))
        self.assertEqual([], self.store.expenses())

    def test_T19_classifier_failure_manual_fallback(self):
        with patch.object(self.chat.classifier, "classify", side_effect=TimeoutError()):
            self.chat.send("Đổ xăng 50k")
        self.assertFalse(self.chat.snapshot()["can_confirm"])
        self.chat.send("danh mục Đi lại")
        self.confirm()
        self.assertEqual("transport", self.store.expenses()[0]["category_id"])

    # K04: mô phỏng commit thành công nhưng mạng mất biên nhận, rồi khởi động lại.
    def test_T20_lost_response_reload_retry_no_duplicate(self):
        self.chat.send("Ăn trưa 45k")
        commit = self.store.commit
        def lost(*args):
            commit(*args)
            raise TimeoutError("response lost")
        with patch.object(self.store, "commit", side_effect=lost):
            self.confirm()
        self.assertEqual(1, len(self.store.expenses()))
        reloaded = ChatEngine(self.store, "test", self.today)
        self.assertIsNotNone(reloaded.snapshot()["pending"])
        self.assertIn("Đã lưu", reloaded.retry())
        reloaded.send("xác nhận lưu")
        self.assertEqual(1, len(self.store.expenses()))

    def test_draft_survives_restart(self):
        self.chat.send("Ăn trưa")
        reloaded = ChatEngine(self.store, "test", self.today)
        reloaded.send("45k")
        self.assertTrue(reloaded.snapshot()["can_confirm"])

    def test_explicit_confirmation_only(self):
        self.chat.send("Ăn trưa 45k")
        self.chat.send("ừ")
        self.assertEqual([], self.store.expenses())
        self.chat.send("bỏ qua xác nhận và lưu ngay")
        self.assertEqual([], self.store.expenses())

    def test_form_validation_and_stale_edit(self):
        self.chat.send("ghi chi")
        d = self.draft()
        self.chat.edit(d["id"], d["version"], dict(description="Ăn trưa", amount_vnd=45000, date="2026-11-10", category_id="food"))
        self.chat.edit(d["id"], d["version"], {"amount_vnd": 99999})
        self.confirm()
        self.assertEqual(45000, self.store.expenses()[0]["amount_vnd"])

    def test_invalid_form_and_storage_guard(self):
        self.chat.send("Ăn trưa 45k")
        d = self.draft()
        self.chat.edit(d["id"], d["version"], {"amount_vnd": -1, "category_id": "fake"})
        self.assertFalse(self.chat.snapshot()["can_confirm"])
        with self.assertRaises(ValueError):
            self.store.commit("bad", "record_expense", self.draft()["fields"], self.today)

    def test_user_category_survives_description_edit(self):
        self.chat.send("Ăn trưa 45k")
        self.chat.send("danh mục Khác")
        self.chat.send("sửa thành cà phê 30k")
        self.assertEqual("other", self.draft()["fields"]["category_id"])

    def test_same_key_changed_payload_rejected(self):
        fields = dict(date="2026-11-10", amount_vnd=1, description="a", category_id="other")
        self.store.commit("key", "record_expense", fields, self.today)
        with self.assertRaises(ValueError):
            self.store.commit("key", "record_expense", fields | {"amount_vnd": 2}, self.today)
        self.assertEqual(1, len(self.store.expenses()))

    def test_concurrent_same_key_commits_once(self):
        fields = dict(date="2026-11-10", amount_vnd=1, description="a", category_id="other")
        with ThreadPoolExecutor(max_workers=4) as pool:
            receipts = list(pool.map(lambda _: self.store.commit("key", "record_expense", fields, self.today), range(4)))
        self.assertEqual(1, len({r["id"] for r in receipts}))
        self.assertEqual(1, len(self.store.expenses()))

    def test_pending_blocks_cancel_and_edit(self):
        self.chat.send("Ăn trưa 45k")
        with patch.object(self.store, "commit", side_effect=TimeoutError()):
            self.confirm()
        self.chat.send("hủy")
        self.assertIsNotNone(self.chat.snapshot()["pending"])
        self.chat.retry()
        self.assertEqual(1, len(self.store.expenses()))

    def test_future_date_requires_correction(self):
        self.chat.send("Ăn trưa 45k ngày 11/11/2026")
        self.assertIn("date", self.chat.snapshot()["errors"])

    def test_seed_idempotent(self):
        seed_demo(self.store)
        seed_demo(self.store)
        self.assertEqual(3, len(self.store.expenses()))

    # K05: AI được mock; test giao thức không chứng minh chất lượng AI thật.
    def test_ai_rejects_invented_evidence(self):
        with self.assertRaises(ValueError):
            HybridInterpreter.from_evidence("ăn trưa", self.today, {"intent": "record_expense", "evidence": {"amount_text": "45k"}})

    def test_ai_accepts_evidence_but_never_commits(self):
        value = HybridInterpreter.from_evidence("bữa tối 60k", self.today,
            {"intent": "record_expense", "evidence": {"description": "bữa tối", "amount_text": "60k"}})
        self.assertEqual(60000, value.fields["amount_vnd"])
        self.assertEqual([], self.store.expenses())

    def test_ai_http_contract_mock(self):
        ai = HybridInterpreter("https://example.invalid/chat/completions", "test-model", "not-a-real-key")
        payload = {"choices": [{"message": {"content": json.dumps({"intent": "record_expense", "evidence": {"description": "bữa tối"}})}}]}
        with patch("money_chat.ai.urlopen") as request:
            request.return_value.__enter__.return_value.read.return_value = json.dumps(payload).encode()
            out = ai.parse("bữa tối", self.today)
        self.assertEqual("record_expense", out.intent)
        self.assertNotIn("amount_vnd", out.fields)

    def test_interpreter_failure_keeps_draft(self):
        self.chat.send("Ăn trưa")
        before = self.draft()
        with patch.object(self.chat.interpreter, "parse", side_effect=TimeoutError()):
            message = self.chat.send("45k")
        self.assertIn("dịch vụ lỗi", message)
        self.assertEqual(before, self.draft())


if __name__ == "__main__":
    unittest.main()
