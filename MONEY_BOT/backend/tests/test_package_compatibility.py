"""Legacy tools must share class/module identity with the packaged backend."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

class CompatibilityTests(unittest.TestCase):
    def test_legacy_engine_and_models_share_identity(self):
        import money_chat.engine as old_engine
        import moneybot.money_chat.engine as new_engine
        import money_chat.models as old_models
        import moneybot.money_chat.models as new_models
        self.assertIs(old_engine, new_engine)
        self.assertIs(old_models, new_models)

    def test_legacy_patch_reaches_packaged_classifier(self):
        import classifier
        from moneybot import classifier as implementation
        self.assertIs(classifier, implementation)
        with patch("classifier.classify", return_value="patched"):
            self.assertEqual(implementation.classify("input"), "patched")

    def test_legacy_store_reopens_database_with_packaged_store(self):
        from money_chat.storage import SQLiteStore as OldStore
        from moneybot.money_chat.storage import SQLiteStore
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "existing.sqlite3"
            OldStore(path)
            self.assertEqual(SQLiteStore(path).expenses(), [])
