"""Một khoản chi mới khi đang có nháp khoản chi không được ghi đè nháp cũ."""
import tempfile
import unittest
from datetime import date
from pathlib import Path

from money_chat.engine import ChatEngine
from money_chat.storage import SQLiteStore


class DraftSwitchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store = SQLiteStore(Path(self.temp.name) / "test.db")
        self.chat = ChatEngine(self.store, "test", date(2026, 11, 10))
        self.chat.send("com tam 45k")

    def draft(self):
        return self.chat.snapshot()["draft"]

    def test_new_expense_keeps_existing_draft(self):
        message = self.chat.send("do xang 50k")
        self.assertIn("hủy và chuyển", message)
        self.assertEqual(("com tam", 45000), (self.draft()["fields"]["description"], self.draft()["fields"]["amount_vnd"]))

    def test_keep_draft_then_edit_and_confirm(self):
        self.chat.send("do xang 50k")
        self.assertIn("Đã giữ bản nháp", self.chat.send("giữ bản nháp"))
        self.chat.send("40k")
        d = self.draft()
        self.assertEqual(("com tam", 40000), (d["fields"]["description"], d["fields"]["amount_vnd"]))
        self.chat.confirm(d["id"], d["version"])
        self.assertEqual([(40000, "com tam")], [(r["amount_vnd"], r["description"]) for r in self.store.expenses()])

    def test_form_edit_keeps_draft_and_clears_pending_switch(self):
        self.chat.send("do xang 50k")
        d = self.draft()
        self.chat.edit(d["id"], d["version"], {"amount_vnd": 47000})
        self.assertEqual(47000, self.draft()["fields"]["amount_vnd"])
        self.assertIsNone(self.chat.snapshot()["switch_text"])
        self.chat.send("hôm qua")  # câu bổ sung tiếp theo vẫn sửa nháp, không bị chặn
        self.assertEqual("2026-11-09", self.draft()["fields"]["date"])

    def test_cancel_and_switch_creates_new_draft_without_saving_old(self):
        self.chat.send("do xang 50k")
        self.chat.send("hủy và chuyển")
        d = self.draft()
        self.assertEqual(("do xang", 50000), (d["fields"]["description"], d["fields"]["amount_vnd"]))
        self.assertEqual([], self.store.expenses())

    def test_corrections_still_edit_the_draft(self):
        self.chat.send("50k")
        self.assertEqual(("com tam", 50000), (self.draft()["fields"]["description"], self.draft()["fields"]["amount_vnd"]))
        self.chat.send("sửa thành phở 60k")
        self.assertEqual(("phở", 60000), (self.draft()["fields"]["description"], self.draft()["fields"]["amount_vnd"]))
        self.assertIsNone(self.chat.snapshot()["switch_text"])


if __name__ == "__main__":
    unittest.main()
