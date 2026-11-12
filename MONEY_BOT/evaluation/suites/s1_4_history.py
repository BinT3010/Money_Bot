"""Việc con 1.4: tổng chi theo danh mục, theo tháng và lịch sử giao dịch khớp 100% với tính tay
trên 5 bộ dữ liệu ≥ 3 tháng, kể cả sau khi sửa hoặc xóa giao dịch.

Mỗi bộ cho 2 tình huống: trước và sau khi sửa/xóa. Mỗi tháng kiểm tra:
- /summary: tổng, số giao dịch, chi từng danh mục (đủ 6, danh mục không chi = 0);
- /transactions: đúng các giao dịch của tháng (số lượng, mã, tổng tiền);
- /summary/history: số liệu biểu đồ theo tháng trùng với /summary.
"""
import json
from pathlib import Path

DATA = Path(__file__).resolve().parents[1] / "data" / "history"
CATEGORIES = ["food", "shopping", "transport", "utilities", "entertainment", "other"]
SUITE = {"suite": "1.4", "title": "Tổng chi theo danh mục/tháng và lịch sử giao dịch, kể cả sau sửa/xóa",
         "threshold": {"min_cases": 10, "pass_rate": 1.0}, "datasets": 5, "min_months": 3}


def check_month(ctx, s, month, expected, live_ids, evidence):
    problems = []
    status, summary, ms = s.request("GET", f"/summary?month={month}")
    evidence.append({"req": f"GET /summary?month={month}", "status": status, "ms": ms})
    actual = {"total": summary.get("total"), "count": summary.get("count"),
              "by_category": {r["category_id"]: r["spent"] for r in summary.get("categories", [])}}
    problems += [f"{month} /summary {p}" for p in ctx.match(expected, actual)]
    status, rows, ms = s.request("GET", f"/transactions?month={month}")
    evidence.append({"req": f"GET /transactions?month={month}", "status": status, "ms": ms})
    if sorted(r["id"] for r in rows) != sorted(live_ids):
        problems.append(f"{month} /transactions: mã giao dịch khác mong đợi ({len(rows)} so với {len(live_ids)})")
    if sum(r["amount_vnd"] for r in rows) != expected["total"]:
        problems.append(f"{month} /transactions: tổng {sum(r['amount_vnd'] for r in rows)} ≠ {expected['total']}")
    return problems, actual


def phase(ctx, s, dataset, ids, rows, expected_key):
    evidence, problems, actual = [], [], {}
    for month, expected in dataset[expected_key].items():
        live = [ids[ref] for ref, t in rows.items() if t["date"][:7] == month]
        p, actual[month] = check_month(ctx, s, month, expected, live, evidence)
        problems += p
    months = sorted(dataset[expected_key])
    count = len(months)
    status, history, ms = s.request("GET", f"/summary/history?month={months[-1]}&months={count}")
    evidence.append({"req": "GET /summary/history", "status": status, "ms": ms})
    chart = {h["month"]: {"total": h["total"], "count": h["count"], "by_category": h["by_category"]} for h in history} if status == 200 else {}
    problems += [f"/summary/history {p}" for p in ctx.match(dataset[expected_key], chart)]
    return problems, actual, evidence


def run(ctx):
    results = []
    for path in sorted(DATA.glob("h*.json")):
        dataset = json.loads(path.read_text(encoding="utf-8"))
        s = ctx.Session(dataset["today"])
        try:
            ids, rows, setup = {}, {}, []
            for t in dataset["transactions"]:
                body = {k: t[k] for k in ("amount_vnd", "description", "category_id", "date")}
                status, data, ms = s.request("POST", "/transactions", body)
                setup.append({"req": f"POST /transactions ({t['ref']})", "status": status, "ms": ms})
                ids[t["ref"]], rows[t["ref"]] = data.get("id"), dict(body)
            problems, actual, evidence = phase(ctx, s, dataset, ids, rows, "expected")
            problems = [f"tạo giao dịch lỗi HTTP {e['status']}" for e in setup if e["status"] != 200] + problems
            results.append({"id": f"1.4-{dataset['id']}-a", "desc": f"{dataset['desc']} — sau khi nhập",
                            "input": {"dataset": path.name, "transactions": len(dataset["transactions"])},
                            "expected": dataset["expected"], "actual": actual, "passed": not problems,
                            "problems": problems, "evidence": setup + evidence})
            edits = []
            for e in dataset["edits"]:
                if e.get("delete"):
                    status, _, ms = s.request("DELETE", f"/transactions/{ids[e['ref']]}")
                    rows.pop(e["ref"])
                else:
                    rows[e["ref"]].update(e["set"])
                    status, _, ms = s.request("PUT", f"/transactions/{ids[e['ref']]}", rows[e["ref"]])
                edits.append({"req": f"{'DELETE' if e.get('delete') else 'PUT'} {e['ref']}", "status": status, "ms": ms})
            problems, actual, evidence = phase(ctx, s, dataset, ids, rows, "expected_after_edits")
            problems = [f"sửa/xóa lỗi HTTP {e['status']}" for e in edits if e["status"] != 200] + problems
            results.append({"id": f"1.4-{dataset['id']}-b", "desc": f"{dataset['desc']} — sau khi sửa/xóa",
                            "input": {"dataset": path.name, "edits": dataset["edits"]},
                            "expected": dataset["expected_after_edits"], "actual": actual, "passed": not problems,
                            "problems": problems, "evidence": edits + evidence})
        except Exception as exc:  # không bỏ qua lỗi chạy
            results.append({"id": f"1.4-{dataset['id']}", "desc": dataset["desc"], "input": path.name, "expected": None,
                            "actual": None, "passed": False, "problems": [f"lỗi khi chạy: {type(exc).__name__}: {exc}"], "evidence": []})
        finally:
            s.close()
    return results
