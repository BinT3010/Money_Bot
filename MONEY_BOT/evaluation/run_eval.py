"""Chạy thực nghiệm Money Bot qua API thật (FastAPI in-process) với ngày cố định.

    python evaluation/run_eval.py                 # mọi bộ
    python evaluation/run_eval.py --suite 3.2 3.4 # chọn bộ theo mã việc con
    python evaluation/run_eval.py --strict        # mã thoát 1 nếu có bộ chưa đạt ngưỡng

Hai loại bộ:
- cases/*.json: kịch bản khai báo (đáp án chuẩn viết trước khi chạy). Định dạng: evaluation/README.md.
- suites/*.py: bộ có tính toán riêng (độ chính xác, tập dữ liệu); khai báo SUITE và run(ctx) -> list kết quả.
Mỗi tình huống chạy trên thư mục dữ liệu và tài khoản riêng. Kết quả: results/<mã>.json và results/SUMMARY.md.
"""
import argparse
import importlib.util
import json
import re
import shutil
import sys
import tempfile
import time
import warnings
from datetime import date
from pathlib import Path
from uuid import uuid4

HERE = Path(__file__).resolve().parent
BACKEND = HERE.parent / "backend"
sys.path.insert(0, str(BACKEND))
warnings.filterwarnings("ignore", message="Using `httpx` with `starlette.testclient`")

from fastapi.testclient import TestClient  # noqa: E402
from app import create_app  # noqa: E402


class Clock:
    def __init__(self, today):
        self.value = date.fromisoformat(today)

    def __call__(self):
        return self.value


class Session:
    """Một người dùng mới trên một kho dữ liệu tạm; mọi request có Authorization và Idempotency-Key."""

    def __init__(self, today="2026-11-12"):
        self.dir = Path(tempfile.mkdtemp(prefix="moneybot-eval-"))
        self.clock = Clock(today)
        self.client = TestClient(create_app(self.dir, self.clock))
        token = self.client.post("/api/auth/register",
                                 json={"email": f"eval-{uuid4().hex[:8]}@moneybot.test", "password": "Password123"}).json()["token"]
        self.client.headers["Authorization"] = f"Bearer {token}"
        self.vars = {}

    def request(self, method, path, body=None, key=None):
        started = time.perf_counter()
        headers = {} if method == "GET" else {"Idempotency-Key": key or uuid4().hex}
        r = self.client.request(method, "/api" + path, json=body, headers=headers)
        ms = round((time.perf_counter() - started) * 1000, 1)
        try:
            data = r.json()
        except ValueError:
            data = r.text
        return r.status_code, data, ms

    def close(self):
        self.client.close()
        shutil.rmtree(self.dir, ignore_errors=True)


# ---------- so khớp đáp án chuẩn ----------

def match(expected, actual, path="$"):
    """Trả về danh sách chỗ lệch. dict = khớp tập con các khóa; list = cùng độ dài, khớp từng phần tử;
    {"$len": n}, {"$contains": [..]} (mỗi phần tử khớp ít nhất một phần tử thực tế), {"$any": true},
    {"$absent": true} (khóa không có hoặc null), {"$approx": x, "tol": t}; giá trị khác so sánh bằng."""
    if isinstance(expected, dict) and any(k.startswith("$") for k in expected):
        if "$any" in expected:
            return []
        if "$absent" in expected:
            return [] if actual is None else [f"{path}: mong đợi không có, thực tế {actual!r}"]
        if "$len" in expected:
            if not isinstance(actual, list) or len(actual) != expected["$len"]:
                return [f"{path}: mong đợi {expected['$len']} phần tử, thực tế {len(actual) if isinstance(actual, list) else actual!r}"]
            return []
        if "$contains" in expected:
            if not isinstance(actual, list):
                return [f"{path}: mong đợi danh sách"]
            return [f"{path}: thiếu phần tử khớp {item!r}" for item in expected["$contains"]
                    if not any(not match(item, a) for a in actual)]
        if "$approx" in expected:
            ok = isinstance(actual, (int, float)) and abs(actual - expected["$approx"]) <= expected.get("tol", 1e-6)
            return [] if ok else [f"{path}: mong đợi ≈{expected['$approx']}, thực tế {actual!r}"]
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            return [f"{path}: mong đợi object, thực tế {actual!r}"]
        out = []
        for k, v in expected.items():
            if k not in actual and not (isinstance(v, dict) and "$absent" in v):
                out.append(f"{path}.{k}: thiếu khóa")
            else:
                out += match(v, actual.get(k), f"{path}.{k}")
        return out
    if isinstance(expected, list):
        if not isinstance(actual, list) or len(actual) != len(expected):
            return [f"{path}: mong đợi {len(expected)} phần tử, thực tế {len(actual) if isinstance(actual, list) else actual!r}"]
        out = []
        for i, (e, a) in enumerate(zip(expected, actual)):
            out += match(e, a, f"{path}[{i}]")
        return out
    return [] if expected == actual else [f"{path}: mong đợi {expected!r}, thực tế {actual!r}"]


def substitute(value, variables):
    if isinstance(value, str):
        whole = re.fullmatch(r"\{(\w+)\}", value)
        if whole and whole.group(1) in variables:
            return variables[whole.group(1)]  # giữ kiểu (số, null)
        return re.sub(r"\{(\w+)\}", lambda m: str(variables.get(m.group(1), m.group(0))), value)
    if isinstance(value, list):
        return [substitute(v, variables) for v in value]
    if isinstance(value, dict):
        return {k: substitute(v, variables) for k, v in value.items()}
    return value


def pick(data, dotted):
    for part in dotted.split("."):
        data = data[int(part)] if isinstance(data, list) else data[part]
    return data


# ---------- bộ khai báo (cases/*.json) ----------

def run_case(case, default_today):
    s = Session(case.get("today", default_today))
    evidence, problems, last = [], [], None
    try:
        for n, step in enumerate(case["steps"], 1):
            if "today" in step and len(step) == 1:
                s.clock.value = date.fromisoformat(step["today"])
                evidence.append({"step": n, "today": step["today"]})
                continue
            if "today" in step:
                s.clock.value = date.fromisoformat(step["today"])
            method, path = step["req"].split(" ", 1)
            body = substitute(step.get("body"), s.vars)
            status, data, ms = s.request(method, substitute(path, s.vars), body, substitute(step.get("key"), s.vars))
            last = data
            evidence.append({"step": n, "req": f"{method} {substitute(path, s.vars)}", "status": status, "ms": ms})
            want_status = step.get("status", 200)
            if status != want_status:
                problems.append(f"bước {n} ({step['req']}): HTTP {status}, mong đợi {want_status}: {json.dumps(data, ensure_ascii=False)[:300]}")
            if "expect" in step:
                problems += [f"bước {n}: {p}" for p in match(step["expect"], data)]
            if "max_ms" in step and ms > step["max_ms"]:
                problems.append(f"bước {n}: {ms} ms > {step['max_ms']} ms")
            for name, dotted in step.get("save", {}).items():
                try:
                    s.vars[name] = pick(data, dotted)
                except (KeyError, IndexError, TypeError):
                    problems.append(f"bước {n}: không lấy được {dotted} để lưu vào {name}")
    except Exception as exc:  # lỗi chạy không được bỏ qua: tính là chưa đạt
        problems.append(f"lỗi khi chạy: {type(exc).__name__}: {exc}")
    finally:
        s.close()
    checks = [{k: v for k, v in step.items() if k in ("req", "expect", "status", "max_ms")} for step in case["steps"]
              if "expect" in step or "max_ms" in step or step.get("status", 200) != 200]
    return {"id": case["id"], "desc": case.get("desc", ""), "input": case["steps"], "expected": checks,
            "actual": last, "passed": not problems, "problems": problems, "evidence": evidence}


def json_suite(path):
    spec = json.loads(path.read_text(encoding="utf-8"))
    return spec, lambda: [run_case(case, spec.get("today", "2026-11-12")) for case in spec["cases"]]


# ---------- bộ Python (suites/*.py) ----------

def py_suite(path):
    module_spec = importlib.util.spec_from_file_location(f"suite_{path.stem}", path)
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    return module.SUITE, lambda: module.run(sys.modules[__name__])


def discover():
    suites = []
    for path in sorted((HERE / "cases").glob("*.json")):
        suites.append(json_suite(path))
    for path in sorted((HERE / "suites").glob("*.py")):
        if not path.name.startswith("_"):
            suites.append(py_suite(path))
    return suites


def evaluate(spec, results):
    threshold = spec.get("threshold", {})
    passed = sum(r["passed"] for r in results)
    total = len(results)
    rate = passed / total if total else 0.0
    met = total >= threshold.get("min_cases", 1) and rate >= threshold.get("pass_rate", 1.0)
    extra = {k: v for k, v in spec.items() if k not in ("suite", "title", "threshold", "cases", "today")}
    return {"suite": spec["suite"], "title": spec.get("title", ""), "threshold": threshold,
            "cases": total, "passed": passed, "pass_rate": round(rate, 4), "met": met, **extra, "results": results}


def summary_markdown(reports):
    lines = ["# Kết quả thực nghiệm", "",
             f"Chạy ngày {date.today().isoformat()} bằng `evaluation/run_eval.py` (API thật, ngày đánh giá cố định theo từng tình huống).", "",
             "| Việc con | Nội dung | Đạt / tổng | Ngưỡng | Kết luận |", "|---|---|---|---|---|"]
    for r in reports:
        t = r["threshold"]
        need = f"≥{t.get('min_cases', 1)} tình huống, đạt {int(t.get('pass_rate', 1.0) * 100)}%"
        lines.append(f"| {r['suite']} | {r['title']} | {r['passed']}/{r['cases']} | {need} | {'Đạt' if r['met'] else 'Chưa đạt'} |")
    failed = [(r["suite"], x) for r in reports for x in r["results"] if not x["passed"]]
    if failed:
        lines += ["", "## Tình huống chưa đạt", ""]
        lines += [f"- **{suite} · {x['id']}** {x['desc']}: " + "; ".join(x["problems"][:3]) for suite, x in failed]
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--suite", nargs="*", help="mã việc con cần chạy, ví dụ 3.2")
    parser.add_argument("--out", default=str(HERE / "results"))
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args(argv)
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")  # console Windows mặc định không phải UTF-8
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    reports = []
    for spec, run in discover():
        if args.suite and spec["suite"] not in args.suite:
            continue
        report = evaluate(spec, run())
        reports.append(report)
        (out / f"{spec['suite']}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{spec['suite']:>5}  {report['passed']}/{report['cases']}  {'ĐẠT' if report['met'] else 'CHƯA ĐẠT'}  {spec.get('title', '')}")
    if not args.suite:  # chỉ bản chạy đầy đủ mới ghi đè bảng tổng hợp
        (out / "SUMMARY.md").write_text(summary_markdown(reports), encoding="utf-8")
    return 1 if args.strict and not all(r["met"] for r in reports) else 0


if __name__ == "__main__":
    sys.exit(main())
