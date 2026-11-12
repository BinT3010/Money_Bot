"""Ngân sách tháng, hạn mức danh mục, cảnh báo ngưỡng và gợi ý hạn mức (G2)."""
import json
from decimal import Decimal, ROUND_HALF_UP

from moneybot.common import month_check, shift_month, integer
from moneybot.money_chat.models import CATEGORIES, money
from moneybot.money_chat.storage import encode


def percent_of(spent, limit):
    """Tỷ lệ sử dụng (%) làm tròn 2 chữ số, tính bằng Decimal để không lệch do float."""
    return (Decimal(spent) * 100 / Decimal(limit)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def percent_text(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return text.replace(".", ",") + "%"


def usage_message(label, spent, limit, percent, month):
    """Câu Ou4 theo mẫu đặc tả: "Ăn uống đã dùng 1.605.000/2.000.000đ (80,25%), còn 395.000đ trong hạn mức tháng 10." """
    if limit is None:
        return None
    m = int(month[5:7])
    if limit == 0:
        return (f"{label}: hạn mức 0đ nhưng đã chi {money(spent)} trong tháng {m}, vượt hạn mức." if spent
                else f"{label}: hạn mức 0đ, chưa chi trong tháng {m}.")
    used = f"{label} đã dùng {money(spent).removesuffix('đ')}/{money(limit)} ({percent_text(percent)})"
    if spent <= limit:
        return f"{used}, còn {money(limit - spent)} trong hạn mức tháng {m}."
    return f"{used}, vượt {money(spent - limit)} so với hạn mức tháng {m}."


def money_text(value):
    """Tiền có thể lẻ (trung bình): số nguyên in như money(); phần lẻ in tối đa 2 chữ số, dấu phẩy thập phân."""
    if float(value).is_integer():
        return money(int(value))
    whole, frac = f"{value:.2f}".split(".")
    return money(int(whole)).removesuffix("đ") + "," + frac.rstrip("0") + "đ"


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
            db.execute("INSERT INTO budgets (month, total, limits, threshold) VALUES (?,?,?,?) ON CONFLICT(month) DO UPDATE SET total=excluded.total, limits=excluded.limits, threshold=excluded.threshold",
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
            percent = percent_of(spent, limit) if limit else None
            rows.append({"category_id": cid, "label": label, "spent": spent, "limit": limit,
                         "level": level, "ratio": spent / limit if limit else None,
                         "percent": float(percent) if percent is not None else None,
                         "remaining": limit - spent if limit is not None else None,
                         "message": usage_message(label, spent, limit, percent, month)})
        return {**s, "budget": b, "remaining": b["total"] - s["total"] if b["total"] is not None else None, "categories": rows}

    def history(self, month, count=6):
        """Ou2: chi theo danh mục của `count` tháng kết thúc ở `month` (cũ -> mới), cho biểu đồ theo tháng."""
        month_check(month)
        integer(count, 1, 24)
        result = []
        for offset in range(count - 1, -1, -1):
            m = shift_month(month, -offset)
            s = self.summary(m)
            result.append({"month": m, "total": s["total"], "count": s["count"],
                           "by_category": {k: s["by_category"].get(k, 0) for k in CATEGORIES}})
        return result

    def suggest(self, month, today):
        b = self.budget(month)
        if month > today.strftime("%Y-%m"):
            raise ValueError("Chỉ đề xuất cho tháng hiện tại hoặc tháng đã qua.")
        months = [shift_month(month, -n) for n in (3, 2, 1)]
        with self.connection() as db:
            first = db.execute("SELECT MIN(date) FROM expenses").fetchone()[0]
        missing = ([] if b["total"] is not None else ["budget"]) + ([] if first and first[:7] <= months[0] else ["history"])
        if missing:
            return {"available": False, "months": months, "missing": missing, "budget_total": b["total"],
                    "reason": "Cần ngân sách tổng và lịch sử bắt đầu ít nhất ba tháng trước; hãy nhập hạn mức thủ công."}
        history = {m: {k: self.summary(m)["by_category"].get(k, 0) for k in CATEGORIES} for m in months}
        totals = {k: sum(history[m][k] for m in months) for k in CATEGORIES}
        aggregate = sum(totals.values())
        if aggregate == 0:
            return {"available": False, "months": months, "missing": [], "budget_total": b["total"], "history": history,
                    "reason": "Ba tháng không có chi tiêu; hãy nhập hạn mức thủ công."}
        total = b["total"]
        # Công thức mục 3.7: L_i = floor(x̄_i · min(1, B/A)), x̄_i = totals_i/3, A = Σx̄_i = aggregate/3.
        # Tính bằng số nguyên để floor chính xác: x̄_i·B/A = totals_i·B/aggregate.
        scaled = aggregate > 3 * total
        limits = {k: v * total // aggregate if scaled else v // 3 for k, v in totals.items()}
        factor = 3 * total / aggregate if scaled else 1.0
        allocated = sum(limits.values())
        factor_text = f"{factor:.4f}".rstrip("0").rstrip(".").replace(".", ",")
        reason = (f"Trung bình 3 tháng {months[0]} – {months[-1]} (tháng không chi tính 0). "
                  + (f"Tổng trung bình A = {money_text(aggregate / 3)} vượt ngân sách B = {money_text(total)}, "
                     f"nên mỗi danh mục nhân hệ số B/A = {factor_text} và làm tròn xuống."
                     if scaled else
                     f"Tổng trung bình A = {money_text(aggregate / 3)} không vượt ngân sách B = {money_text(total)}, "
                     "nên giữ nguyên trung bình (hệ số 1) và làm tròn xuống.")
                  + f" Phần chưa phân bổ: {money_text(total - allocated)}. Dữ liệu nhập được giả định đầy đủ.")
        return {"available": True, "months": months, "limits": limits, "reason": reason,
                "history": history, "totals": totals,
                "averages": {k: round(v / 3, 2) for k, v in totals.items()},
                "average_total": round(aggregate / 3, 2), "budget_total": total,
                "factor": round(factor, 6), "allocated": allocated, "unallocated": total - allocated}
