"""V01–V03: các điểm nối với phần Cường, Phúc, Dũng."""
from typing import Protocol
from .parser import fold


# V01: chỉ định chữ ký cần có; engine không phụ thuộc cách triển khai cụ thể.
class Classifier(Protocol):
    def classify(self, description: str) -> str | None: ...


class Backend(Protocol):
    def commit(self, key, intent, fields, today) -> dict: ...
    def operation_status(self, key) -> dict | None: ...
    def summary(self, month, category_id=None) -> dict: ...
    def due_bills(self, today) -> dict: ...


# V02: baseline minh bạch, không tuyên bố đây là mô hình AI của Cường.
class KeywordClassifier:
    def classify(self, description):
        text = fold(description)
        if "xe do choi" in text:
            return "shopping"
        if "sach hoc" in text or "thuoc" in text:
            return "other"
        if "sach" in text and not any(w in text for w in ["truyen", "giai tri"]):
            return None
        rules = {
            "food": ["an trua", "an sang", "an toi", "ca phe", "tra sua", "do uong"],
            "shopping": ["quan ao", "ban hoc", "sac dien thoai", "my pham", "do choi"],
            "transport": ["do xang", "ve xe", "xe buyt", "gui xe", "grab"],
            "utilities": ["tien phong", "tien dien", "tien nuoc", "tien mang", "internet"],
            "entertainment": ["xem phim", "netflix", "spotify", "truyen", "sach giai tri"],
        }
        found = {key for key, words in rules.items() if any(w in text for w in words)}
        return next(iter(found)) if len(found) == 1 else None
