"""Ngân sách tháng, hạn mức danh mục, cảnh báo ngưỡng và gợi ý hạn mức (G2)."""
import json

from common import month_check, shift_month, integer
from money_chat.models import CATEGORIES
from money_chat.storage import encode


class BudgetMixin:
    """Dùng chung connection()/summary() của FinanceStore; không tự mở kho dữ liệu khác."""

    def budget(self, month):
        month_check(month)
        with self.connection() as db:
            row = db.execute("SELECT * FROM budgets WHERE month=?", (month,)).fetchone()
        return {"month": month, "total": row["total"] if row else None,
                "limits": json.loads(row["limits"]) if row else {}, "threshold": row["threshold"] if row else 80}

    def set_budget(self, month, total, limits, threshold):
        month_check(month)
        integer(total, 1)
        integer(threshold, 1, 99)
        if not isinstance(limits, dict) or any(k not in CATEGORIES for k in limits):
            raise ValueError("Danh mục không hợp lệ.")
        for value in limits.values():
            integer(value)
        if sum(limits.values()) > total:
            raise ValueError("Tổng hạn mức danh mục vượt ngân sách tổng.")
        with self.connection() as db:
            db.execute("INSERT INTO budgets VALUES (?,?,?,?) ON CONFLICT(month) DO UPDATE SET total=excluded.total, limits=excluded.limits, threshold=excluded.threshold",
                       (month, total, encode(limits), threshold))
        return self.budget(month)

    def overview(self, month):
        b, s = self.budget(month), self.summary(month)
        rows = []
        for cid, label in CATEGORIES.items():
            spent, limit = s["by_category"].get(cid, 0), b["limits"].get(cid)
            level = "UNSET"
            if limit is not None:
                level = ("OVER" if spent > 0 else "NORMAL") if limit == 0 else (
                    "OVER" if spent > limit else "REACHED" if spent == limit else
                    "WARNING" if spent * 100 >= limit * b["threshold"] else "NORMAL")
            rows.append({"category_id": cid, "label": label, "spent": spent, "limit": limit,
                         "level": level, "ratio": spent / limit if limit else None})
        return {**s, "budget": b, "remaining": b["total"] - s["total"] if b["total"] is not None else None, "categories": rows}

    def suggest(self, month, today):
        b = self.budget(month)
        if month > today.strftime("%Y-%m"):
            raise ValueError("Chỉ đề xuất cho tháng hiện tại hoặc tháng đã qua.")
        months = [shift_month(month, -n) for n in (3, 2, 1)]
        with self.connection() as db:
            first = db.execute("SELECT MIN(date) FROM expenses").fetchone()[0]
        if not first or first[:7] > months[0] or b["total"] is None:
            return {"available": False, "months": months, "reason": "Cần ngân sách tổng và lịch sử bắt đầu ít nhất ba tháng trước; hãy nhập hạn mức thủ công."}
        totals = {k: sum(self.summary(m)["by_category"].get(k, 0) for m in months) for k in CATEGORIES}
        aggregate = sum(totals.values())
        if aggregate == 0:
            return {"available": False, "months": months, "reason": "Ba tháng không có chi tiêu; hãy nhập hạn mức thủ công."}
        # Tính bằng số nguyên để floor chính xác; A=sum(totals)/3.
        limits = {k: v // 3 if aggregate <= 3 * b["total"] else v * b["total"] // aggregate for k, v in totals.items()}
        return {"available": True, "months": months, "limits": limits,
                "reason": "Trung bình 3 tháng đầy đủ, co theo ngân sách tổng; tháng không chi tính 0. Dữ liệu nhập được giả định đầy đủ."}
