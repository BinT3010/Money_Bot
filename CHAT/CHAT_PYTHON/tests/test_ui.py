"""Kiểm tra Streamlit thật bằng AppTest; cần cài requirements.txt."""
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from streamlit.testing.v1 import AppTest
from money_chat.storage import SQLiteStore


class UITests(unittest.TestCase):
    def test_chat_confirm_and_reload(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"MONEY_CHAT_DATA_DIR": temp, "MONEY_CHAT_AI_URL": ""}):
            app = AppTest.from_file(str(Path(__file__).resolve().parents[1] / "app.py"), default_timeout=20).run()
            self.assertFalse(app.exception)
            app.chat_input[0].set_value("Ăn trưa 45k").run()
            self.assertFalse(app.exception)
            store = SQLiteStore(Path(temp) / "chat.db")
            self.assertEqual([], store.expenses())
            next(b for b in app.button if b.label == "Xác nhận lưu").click().run()
            self.assertFalse(app.exception)
            self.assertEqual(45000, store.expenses()[0]["amount_vnd"])
            app.run()
            self.assertEqual(1, len(store.expenses()))
            # AppTest mới mô phỏng mất session_state của trình duyệt.
            reloaded = AppTest.from_file(str(Path(__file__).resolve().parents[1] / "app.py"), default_timeout=20)
            reloaded.query_params["session"] = app.query_params["session"]
            reloaded.run()
            self.assertFalse(reloaded.exception)
            self.assertTrue(any("Đã lưu" in m.value for m in reloaded.markdown))

    def test_form_edit_reclassifies_and_cancel_does_not_save(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"MONEY_CHAT_DATA_DIR": temp, "MONEY_CHAT_AI_URL": ""}):
            app = AppTest.from_file(str(Path(__file__).resolve().parents[1] / "app.py"), default_timeout=20).run()
            app.chat_input[0].set_value("Vé xe 30k").run()
            app.text_input[0].set_value("Mua xe đồ chơi")
            next(b for b in app.button if b.label == "Cập nhật bản nháp").click().run()
            self.assertEqual("shopping", app.selectbox[0].value)
            next(b for b in app.button if b.label == "Hủy bản nháp").click().run()
            self.assertFalse(app.exception)
            self.assertEqual([], SQLiteStore(Path(temp) / "chat.db").expenses())


if __name__ == "__main__":
    unittest.main()
