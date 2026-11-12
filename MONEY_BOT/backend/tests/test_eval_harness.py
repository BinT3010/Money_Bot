"""D09: kiểm tra bộ chạy thực nghiệm evaluation/run_eval.py (so khớp, biến, đổi ngày, ngưỡng)."""
import importlib.util
from pathlib import Path

import pytest

PATH = Path(__file__).resolve().parents[2] / "evaluation" / "run_eval.py"
spec = importlib.util.spec_from_file_location("run_eval", PATH)
run_eval = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_eval)


@pytest.mark.parametrize("expected,actual,ok", [
    ({"a": 1}, {"a": 1, "b": 2}, True),
    ({"a": 1}, {"a": 2}, False),
    ({"a": {"$absent": True}}, {}, True),
    ({"a": {"$absent": True}}, {"a": None}, True),
    ({"a": {"$absent": True}}, {"a": 0}, False),
    ([{"x": 1}, {"x": 2}], [{"x": 1, "y": 0}, {"x": 2}], True),
    ([{"x": 1}], [{"x": 1}, {"x": 2}], False),
    ({"$len": 2}, [1, 2], True),
    ({"$len": 2}, [1], False),
    ({"$contains": [{"x": 2}]}, [{"x": 1}, {"x": 2}], True),
    ({"$contains": [{"x": 3}]}, [{"x": 1}], False),
    ({"$approx": 0.8, "tol": 1e-6}, 0.8000001, True),
    ({"$approx": 0.8}, 0.81, False),
    ({"$any": True}, "anything", True),
    ("2026-11-12", "2026-11-12", True),
])
def test_match(expected, actual, ok):
    assert (run_eval.match(expected, actual) == []) is ok


def test_substitute_keeps_types():
    v = {"id": "abc", "n": 5}
    assert run_eval.substitute({"a": "{n}", "b": "/bills/{id}/pay", "c": ["{id}"]}, v) == {"a": 5, "b": "/bills/abc/pay", "c": ["abc"]}
    assert run_eval.substitute("{missing}", v) == "{missing}"


BILL = {"name": "Điện", "amount_vnd": 500000, "due_day": 15, "start_month": "2026-11"}


def test_run_case_with_save_and_clock():
    case = {"id": "t-1", "today": "2026-11-12", "steps": [
        {"req": "POST /bills", "body": BILL, "save": {"bill": "id"}},
        {"req": "GET /bills", "expect": {"reminders": [{"name": "Điện", "days": 3}]}},
        {"today": "2026-11-14"},
        {"req": "GET /bills", "expect": {"reminders": [{"days": 1}]}, "max_ms": 5000},
        {"req": "POST /bills/{bill}/pay", "body": {"month": "2026-11", "paid_on": "2026-11-14"}, "expect": {"already_paid": False}},
        {"req": "POST /bills/{bill}/pay", "body": {"month": "2026-12", "paid_on": "2026-11-14"}, "status": 400},
    ]}
    r = run_eval.run_case(case, "2026-11-12")
    assert r["passed"], r["problems"]
    assert [e.get("status") for e in r["evidence"]] == [200, 200, None, 200, 200, 400]
    assert all(e["ms"] >= 0 for e in r["evidence"] if "ms" in e)


def test_saved_variables_are_substituted_in_expect():
    case = {"id": "t-5", "steps": [
        {"req": "POST /bills", "body": BILL, "save": {"bill": "id"}},
        {"req": "GET /bills", "expect": {"reminders": [{"notice_id": "{bill}:2026-11:3", "id": "{bill}"}]}},
    ]}
    assert run_eval.run_case(case, "2026-11-12")["passed"]
    case["steps"][1]["expect"]["reminders"][0]["notice_id"] = "{bill}:2026-11:1"
    assert not run_eval.run_case(case, "2026-11-12")["passed"]


def test_run_case_reports_mismatch_and_errors_without_crashing():
    wrong = run_eval.run_case({"id": "t-2", "steps": [
        {"req": "POST /bills", "body": BILL},
        {"req": "GET /bills", "expect": {"reminders": [{"days": 2}]}},
    ]}, "2026-11-12")
    assert not wrong["passed"] and "mong đợi 2, thực tế 3" in wrong["problems"][0]
    broken = run_eval.run_case({"id": "t-3", "steps": [{"req": "GET /bills", "save": {"x": "nope.0"}}]}, "2026-11-12")
    assert not broken["passed"] and "không lấy được" in broken["problems"][0]
    status = run_eval.run_case({"id": "t-4", "steps": [{"req": "POST /bills", "body": {**BILL, "due_day": 40}}]}, "2026-11-12")
    assert not status["passed"] and "HTTP 400" in status["problems"][0]


def test_threshold_requires_case_count_and_rate():
    spec = {"suite": "x", "threshold": {"min_cases": 3, "pass_rate": 1.0}}
    ok = {"passed": True}
    assert not run_eval.evaluate(spec, [ok, ok])["met"]             # thiếu số tình huống
    assert run_eval.evaluate(spec, [ok, ok, ok])["met"]
    assert not run_eval.evaluate(spec, [ok, ok, {"passed": False}])["met"]
