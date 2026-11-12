"""Việc con 2.1: gợi ý hạn mức từ ≥ 3 tháng lịch sử và ngân sách tổng, có nêu căn cứ.
Ngưỡng: khớp 100% công thức mục 3.7 trên 5 bộ dữ liệu; tổng đề xuất không vượt ngân sách; phản hồi ≤ 5 giây;
kiểm tra cả trường hợp thiếu lịch sử / thiếu ngân sách. Đáp án chuẩn: expected_suggestion trong data/history/.
"""
import json
from pathlib import Path

DATA = Path(__file__).resolve().parents[1] / "data" / "history"
SUITE = {"suite": "2.1", "title": "Gợi ý hạn mức từ 3 tháng lịch sử và ngân sách tổng, có căn cứ",
         "threshold": {"min_cases": 7, "pass_rate": 1.0}, "max_ms": 5000}


def seed(s, dataset):
    for t in dataset["transactions"]:
        s.request("POST", "/transactions", {k: t[k] for k in ("amount_vnd", "description", "category_id", "date")})


def run(ctx):
    results = []
    for path in sorted(DATA.glob("h*.json")):
        dataset = json.loads(path.read_text(encoding="utf-8"))
        s = ctx.Session(dataset["today"])
        try:
            seed(s, dataset)
            month, total = dataset["budget"]["month"], dataset["budget"]["total"]
            s.request("PUT", f"/budgets/{month}", {"total": total, "limits": {}})
            status, data, ms = s.request("GET", f"/budgets/{month}/suggestions")
            gold = dataset["expected_suggestion"]
            expected = {"available": True, "limits": gold["limits"], "factor": {"$approx": gold["factor"], "tol": 1e-6},
                        "allocated": gold["allocated"], "unallocated": gold["unallocated"], "budget_total": total}
            problems = ctx.match(expected, data) if status == 200 else [f"HTTP {status}: {data}"]
            if status == 200:
                if sum(data["limits"].values()) > total:
                    problems.append("tổng đề xuất vượt ngân sách")
                for key in ("history", "averages", "average_total", "reason"):
                    if key not in data:
                        problems.append(f"thiếu căn cứ: {key}")
                if "Phần chưa phân bổ" not in data.get("reason", ""):
                    problems.append("reason không nêu phần chưa phân bổ")
            if ms > SUITE["max_ms"]:
                problems.append(f"{ms} ms > {SUITE['max_ms']} ms")
            results.append({"id": f"2.1-{dataset['id']}", "desc": dataset["desc"],
                            "input": {"dataset": path.name, "budget": dataset["budget"]}, "expected": gold,
                            "actual": {k: data.get(k) for k in ("limits", "factor", "allocated", "unallocated", "reason")} if isinstance(data, dict) else data,
                            "passed": not problems, "problems": problems,
                            "evidence": [{"req": f"GET /budgets/{month}/suggestions", "status": status, "ms": ms}]})
        finally:
            s.close()

    # Thiếu dữ liệu: không đề xuất, yêu cầu bổ sung/nhập tay.
    edge = [
        ("2.1-thieu-lich-su", "Có ngân sách nhưng lịch sử chỉ có 2 tháng",
         [("2026-09-05", 500000), ("2026-10-05", 500000)], 2000000, ["history"]),
        ("2.1-thieu-ngan-sach", "Đủ 3 tháng lịch sử nhưng chưa nhập ngân sách tổng",
         [("2026-08-05", 500000), ("2026-09-05", 500000), ("2026-10-05", 500000)], None, ["budget"]),
    ]
    for case_id, desc, rows, total, missing in edge:
        s = ctx.Session("2026-11-12")
        try:
            for day, amount in rows:
                s.request("POST", "/transactions", {"amount_vnd": amount, "description": "Ăn uống", "category_id": "food", "date": day})
            if total:
                s.request("PUT", "/budgets/2026-11", {"total": total, "limits": {}})
            status, data, ms = s.request("GET", "/budgets/2026-11/suggestions")
            expected = {"available": False, "missing": missing, "limits": {"$absent": True}}
            problems = ctx.match(expected, data) if status == 200 else [f"HTTP {status}"]
            if "thủ công" not in str(data.get("reason", "")):
                problems.append("không hướng dẫn nhập hạn mức thủ công")
            results.append({"id": case_id, "desc": desc, "input": {"transactions": rows, "budget": total}, "expected": expected,
                            "actual": data, "passed": not problems, "problems": problems,
                            "evidence": [{"req": "GET /budgets/2026-11/suggestions", "status": status, "ms": ms}]})
        finally:
            s.close()
    return results
