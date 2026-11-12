"""Kiểm thử API thật với database tạm, không đụng dữ liệu sử dụng ứng dụng."""
import io
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFont
from app import create_app
from finance import FinanceStore
from ocr import receipt_fields, extract_text

TODAY = date(2026, 11, 12)


@pytest.fixture
def env(tmp_path):
    app = create_app(tmp_path, lambda: TODAY)
    with TestClient(app) as c:
        r = c.post("/api/auth/register", json={"email": "duy@example.com", "password": "Password123"})
        assert r.status_code == 200, r.text
        c.headers["Authorization"] = "Bearer " + r.json()["token"]
        yield c, tmp_path, r.json()["user_id"]


def write(c, method, path, body=None, key=None):
    return c.request(method, "/api" + path, json=body, headers={"Idempotency-Key": key or uuid4().hex})


def expense(**kw):
    return {"amount_vnd": 45000, "description": "Ăn trưa", "category_id": "food", "date": "2026-11-12", **kw}


def test_chat_to_stats_restart_and_retry(env):
    c, path, uid = env
    k = uuid4().hex
    request = {"action": "send", "text": "ăn trưa 45k"}
    r = write(c, "POST", "/chat/android", request, k)
    assert r.status_code == 200, r.text
    draft = r.json()["draft"]
    assert r.json()["can_confirm"]
    assert write(c, "POST", "/chat/android", request, k).json() == r.json()
    assert c.get("/api/summary?month=2026-11").json()["total"] == 0
    confirm = {"action": "confirm", "draft_id": draft["id"], "version": draft["version"]}
    k2 = uuid4().hex
    confirmed = write(c, "POST", "/chat/android", confirm, k2)
    assert confirmed.json()["draft"] is None
    assert write(c, "POST", "/chat/android", confirm, k2).json() == confirmed.json()
    write(c, "POST", "/chat/android", confirm)  # stale confirmation must not duplicate
    assert c.get("/api/summary?month=2026-11").json()["total"] == 45000
    with TestClient(create_app(path, lambda: TODAY)) as restarted:
        restarted.headers.update(c.headers)
        assert restarted.get("/api/summary?month=2026-11").json()["total"] == 45000
        assert restarted.get("/api/chat/android").json()["last_receipt"]


def test_missing_category_cancel_and_stale_edit(env):
    c, _, _ = env
    first = write(c, "POST", "/chat/android", {"action": "send", "text": "mua đồ 100k"}).json()
    assert "category_id" in first["errors"] and not first["can_confirm"]
    d = first["draft"]
    changed = write(c, "POST", "/chat/android", {"action": "edit", "draft_id": d["id"], "version": d["version"], "fields": {"category_id": "other"}}).json()
    assert changed["can_confirm"]
    assert write(c, "POST", "/chat/android", {"action": "cancel", "draft_id": d["id"], "version": d["version"]}).status_code == 400
    d = changed["draft"]
    write(c, "POST", "/chat/android", {"action": "cancel", "draft_id": d["id"], "version": d["version"]})
    assert c.get("/api/transactions?month=2026-11").json() == []


def test_idempotency_concurrent_and_conflicting_payload(env):
    c, _, _ = env
    k = uuid4().hex
    with ThreadPoolExecutor(max_workers=4) as pool:
        responses = list(pool.map(lambda _: write(c, "POST", "/transactions", expense(), k), range(8)))
    assert all(r.status_code == 200 for r in responses)
    assert len({r.json()["id"] for r in responses}) == 1
    assert write(c, "POST", "/transactions", expense(amount_vnd=90000), k).status_code == 400
    assert c.get("/api/summary?month=2026-11").json()["count"] == 1


def test_auth_isolation_logout_password(env):
    c, _, _ = env
    write(c, "POST", "/transactions", expense())
    old = c.headers["Authorization"]
    second = c.post("/api/auth/register", json={"email": "other@example.com", "password": "Password123"}).json()
    c.headers["Authorization"] = "Bearer " + second["token"]
    assert c.get("/api/summary?month=2026-11").json()["total"] == 0
    assert c.post("/api/auth/change-password", json={"old_password": "Password123", "new_password": "Different123"}).status_code == 200
    assert c.get("/api/categories").status_code == 401
    c.headers["Authorization"] = old
    c.post("/api/auth/logout")
    assert c.get("/api/categories").status_code == 401


@pytest.mark.parametrize("field,value", [("amount_vnd", -1), ("amount_vnd", True), ("amount_vnd", 1.2), ("date", "2026-02-31"), ("date", "2027-01-01"), ("date", "20261112"), ("category_id", "bills"), ("description", "")])
def test_validation(env, field, value):
    c, _, _ = env
    assert write(c, "POST", "/transactions", expense(**{field: value})).status_code in (400, 422)


def test_budget_boundaries_and_recompute(env):
    c, _, _ = env
    write(c, "PUT", "/budgets/2026-11", {"total": 100000, "limits": {"food": 100000, "utilities": 0}, "threshold": 80})
    rid = write(c, "POST", "/transactions", expense(amount_vnd=80000)).json()["id"]
    def level():
        return c.get("/api/summary?month=2026-11").json()["categories"][0]["level"]
    assert level() == "WARNING"
    write(c, "PUT", f"/transactions/{rid}", expense(amount_vnd=100000))
    assert level() == "REACHED"
    write(c, "PUT", f"/transactions/{rid}", expense(amount_vnd=100001))
    assert level() == "OVER"
    write(c, "DELETE", f"/transactions/{rid}")
    assert level() == "NORMAL"
    write(c, "POST", "/transactions", expense(amount_vnd=1, category_id="utilities"))
    cats = {r["category_id"]: r for r in c.get("/api/summary?month=2026-11").json()["categories"]}
    assert cats["utilities"]["level"] == "OVER" and cats["shopping"]["level"] == "UNSET"
    assert write(c, "PUT", "/budgets/2026-11", {"total": 1, "limits": {"food": 2}, "threshold": 80}).status_code == 400


def test_budget_three_full_months(env):
    c, _, _ = env
    write(c, "PUT", "/budgets/2026-11", {"total": 90000, "limits": {}, "threshold": 70})
    assert not c.get("/api/budgets/2026-11/suggestions").json()["available"]
    for month in (8, 9, 10):
        write(c, "POST", "/transactions", expense(date=f"2026-{month:02d}-01", amount_vnd=100000))
        write(c, "POST", "/transactions", expense(date=f"2026-{month:02d}-02", category_id="transport", amount_vnd=50000))
    limits = c.get("/api/budgets/2026-11/suggestions").json()["limits"]
    assert limits["food"] == 60000 and limits["transport"] == 30000
    assert sum(limits.values()) == 90000


def test_bills_old_period_payment_link_and_no_duplicate(env):
    c, _, _ = env
    b = write(c, "POST", "/bills", {"name": "Tiền điện", "amount_vnd": 45000, "due_day": 31, "start_month": "2026-09"}).json()["id"]
    rows = c.get("/api/bills").json()["periods"]
    assert [r["due_date"] for r in rows] == ["2026-09-30", "2026-10-31", "2026-11-30"]
    existing = write(c, "POST", "/transactions", expense(category_id="utilities")).json()["id"]
    payload = {"month": "2026-09", "paid_on": "2026-11-12", "expense_id": existing}
    assert write(c, "POST", f"/bills/{b}/pay", payload).status_code == 200
    assert write(c, "POST", f"/bills/{b}/pay", payload).json()["already_paid"]
    assert c.get("/api/summary?month=2026-11").json()["count"] == 1
    assert write(c, "POST", f"/bills/{b}/pay", {**payload, "month": "2026-10"}).status_code == 400
    assert write(c, "DELETE", f"/transactions/{existing}").status_code == 400
    assert write(c, "POST", f"/bills/{b}/pay", {**payload, "month": "2026-10", "expense_id": None}).status_code == 200
    assert c.get("/api/summary?month=2026-11").json()["count"] == 2


def test_reminders_obligations_month_boundary(tmp_path):
    s = FinanceStore(tmp_path / "f.db")
    for day in (12, 13, 15, 20):
        s.commit(str(day), "create_recurring_bill", {"name": str(day), "amount_vnd": 100, "due_day": day, "start_month": "2026-10"}, TODAY)
    assert len(s.reminders(TODAY)) == 3
    assert s.reminders(TODAY) == s.reminders(TODAY)
    assert s.obligations(TODAY)["obligations"] == 300
    assert len(s.obligations(TODAY)["older_unpaid"]) == 4
    assert s.obligations(TODAY)["difference"] is None
    assert s.obligations(date(2026, 11, 29))["end"] == "2026-11-30"


def test_atomic_rollback(tmp_path):
    s = FinanceStore(tmp_path / "f.db")
    def broken():
        s.commit("expense-key", "record_expense", expense(), TODAY)
        raise ValueError("simulate crash before response")
    with pytest.raises(ValueError):
        s.once("request-key", {}, broken)
    assert s.expenses() == [] and s.operation_status("expense-key") is None


def test_ocr_parser_never_guesses_maximum():
    f, errors = receipt_fields("CAFE\nTOTAL 45,000\nCASH 100,000\n12/11/2026", TODAY)
    assert f["amount_vnd"] == 45000 and not errors
    f, errors = receipt_fields("CAFE\nTOTAL 45,000\nTOTAL 55,000", TODAY)
    assert "amount_vnd" not in f and "amount_vnd" in errors
    assert receipt_fields("CAFE\nTOTAL 45,000.00", TODAY)[0]["amount_vnd"] == 45000
    foreign, issues = receipt_fields("CAFE\nTOTAL USD 45.00", TODAY)
    assert "amount_vnd" not in foreign and issues["amount_vnd"]


@pytest.mark.ocr
def test_real_image_ocr_to_draft_without_save(env):
    c, _, _ = env
    image = Image.new("RGB", (1000, 600), "white")
    font_path = "C:/Windows/Fonts/arial.ttf"
    try:
        font = ImageFont.truetype(font_path, 48)
    except OSError:
        font = ImageFont.load_default(size=48)
    ImageDraw.Draw(image).multiline_text((60, 50), "CAFE MONEY BOT\nTOTAL 45,000\n12/11/2026", font=font, fill="black", spacing=30)
    buf = io.BytesIO()
    image.save(buf, "PNG")
    key = uuid4().hex
    r = c.post("/api/chat/android/receipt", files={"file": ("receipt.png", buf.getvalue(), "image/png")}, headers={"Idempotency-Key": key})
    assert r.status_code == 200, r.text
    assert r.json()["draft"]["fields"]["amount_vnd"] == 45000, r.json()
    assert c.get("/api/transactions?month=2026-11").json() == []
    # Retry không phụ thuộc OCR phải chạy lại thành công và không tạo nháp thứ hai.
    from unittest.mock import patch
    with patch("app.extract_text", side_effect=RuntimeError("OCR unavailable on retry")):
        again = c.post("/api/chat/android/receipt", files={"file": ("receipt.png", buf.getvalue(), "image/png")}, headers={"Idempotency-Key": key})
        assert again.json() == r.json()


@pytest.mark.ocr
def test_real_scanned_pdf_ocr():
    image = Image.new("RGB", (1000, 600), "white")
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 48)
    except OSError:
        font = ImageFont.load_default(size=48)
    ImageDraw.Draw(image).multiline_text((60, 50), "CAFE MONEY BOT\nTOTAL 45,000\n12/11/2026", font=font, fill="black", spacing=30)
    buf = io.BytesIO()
    image.save(buf, "PDF")
    text = extract_text(buf.getvalue())
    assert receipt_fields(text, TODAY)[0]["amount_vnd"] == 45000, text


def test_invalid_upload(env):
    c, _, _ = env
    r = c.post("/api/chat/android/receipt", files={"file": ("bad.png", b"not an image", "image/png")}, headers={"Idempotency-Key": uuid4().hex})
    assert r.status_code == 400


def test_pdf_text_layer_without_ocr():
    from unittest.mock import patch
    stream = b"BT /F1 20 Tf 40 750 Td (CAFE MONEY BOT) Tj 0 -40 Td (TOTAL 45,000) Tj 0 -40 Td (12/11/2026) Tj ET"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    result = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, obj in enumerate(objects, 1):
        offsets.append(len(result))
        result.extend(f"{i} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = len(result)
    result.extend(b"xref\n0 6\n0000000000 65535 f \n")
    for offset in offsets:
        result.extend(f"{offset:010d} 00000 n \n".encode())
    result.extend(f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF".encode())
    with patch("ocr.image_text", side_effect=AssertionError("Text PDF must not require OCR")):
        assert receipt_fields(extract_text(bytes(result)), TODAY)[0]["amount_vnd"] == 45000
