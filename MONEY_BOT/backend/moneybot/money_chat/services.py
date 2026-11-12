"""V01–V03: các điểm nối với phần Cường, Phúc, Dũng."""
from typing import Protocol
from moneybot.classification import TeamClassifier


# V01: chỉ định chữ ký cần có; engine không phụ thuộc cách triển khai cụ thể.
class Classifier(Protocol):
    def classify(self, description: str) -> str | None: ...


class Backend(Protocol):
    def commit(self, key, intent, fields, today) -> dict: ...
    def operation_status(self, key) -> dict | None: ...
    def summary(self, month, category_id=None) -> dict: ...
    def due_bills(self, today) -> dict: ...


# Tên cũ được giữ để tương thích import; chỉ còn một bộ phân loại của nhóm.
class KeywordClassifier(TeamClassifier):
    pass
