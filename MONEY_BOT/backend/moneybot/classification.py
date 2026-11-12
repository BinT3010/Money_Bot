"""Adapter duy nhất cho bộ phân loại của Cường; parser Chat vẫn kiểm tra tiền/ngày."""
from moneybot.classifier import classify
from moneybot.money_chat.models import CATEGORIES

LABEL_TO_ID = {label: cid for cid, label in CATEGORIES.items()}


class TeamClassifier:
    def classify(self, description):
        return LABEL_TO_ID.get(classify(description)["danh_muc"])

    def explain(self, text, today):
        result = classify(text, today=today)
        return {"category_id": LABEL_TO_ID.get(result["danh_muc"]),
                "needs_choice": result["danh_muc"] is None,
                "candidates": [LABEL_TO_ID.get(v, v) for v in result["ung_vien"]],
                "matched_keywords": result["tu_khoa_khop"]}
