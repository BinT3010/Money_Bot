"""API chung cho ứng dụng Java. Chạy một hoặc nhiều worker: SQLite khóa transaction.
Mỗi người dùng có DB riêng, server lấy đường dẫn từ token chứ không nhận từ client.
"""
import hashlib
import os
import re
from datetime import date
from pathlib import Path
from typing import Annotated, Literal

from fastapi import FastAPI, Depends, Header, HTTPException, UploadFile, File, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from pydantic import BaseModel, Field, ConfigDict, StrictInt
from auth import Auth
from finance import FinanceStore, month_check
from classification import TeamClassifier
from money_chat.engine import ChatEngine
from money_chat.models import CATEGORIES, Draft, local_today
from ocr import extract_text, receipt_fields


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Credentials(StrictModel):
    email: str = Field(max_length=254)
    password: str = Field(min_length=1, max_length=128)


class PasswordChange(StrictModel):
    old_password: str = Field(max_length=128)
    new_password: str = Field(max_length=128)


class ChatAction(StrictModel):
    action: Literal["send", "edit", "confirm", "cancel", "retry"]
    text: str = Field(default="", max_length=2000)
    draft_id: str = ""
    version: StrictInt = 0
    fields: dict = Field(default_factory=dict)


class Expense(StrictModel):
    amount_vnd: StrictInt
    description: str = Field(max_length=300)
    category_id: str
    date: str


class Bill(StrictModel):
    name: str = Field(max_length=300)
    amount_vnd: StrictInt
    due_day: StrictInt
    start_month: str


class Payment(StrictModel):
    month: str
    paid_on: str
    expense_id: str | None = None


class Budget(StrictModel):
    total: StrictInt
    limits: dict[str, StrictInt]
    threshold: StrictInt = 80


def create_app(data_dir=None, today_provider=None):
    root = Path(data_dir or os.getenv("MONEYBOT_DATA", Path(__file__).parent / "data")).resolve()
    root.mkdir(parents=True, exist_ok=True)
    auth = Auth(root / "accounts.sqlite3")
    today = today_provider or local_today
    app = FastAPI(title="Money Bot", version="2.0.0", description="Python API + Android Java. Mọi thao tác ghi cần xác nhận và khóa chống lặp.")

    @app.exception_handler(ValueError)
    async def bad_value(request, exc):
        return JSONResponse(status_code=400, content={"message": str(exc)})

    @app.exception_handler(RequestValidationError)
    async def bad_schema(request, exc):
        return JSONResponse(status_code=422, content={"message": "Dữ liệu không đúng cấu trúc hoặc kiểu. Kiểm tra các trường bắt buộc."})

    # Lớp gốc của Starlette: gồm cả 404/405 do router sinh ra, không chỉ HTTPException của FastAPI.
    @app.exception_handler(StarletteHTTPException)
    async def http_error(request, exc):
        return JSONResponse(status_code=exc.status_code, content={"message": str(exc.detail)}, headers=getattr(exc, "headers", None))

    def token(authorization: Annotated[str | None, Header()] = None):
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(401, "Vui lòng đăng nhập.")
        return authorization[7:]

    def user(t: str = Depends(token)):
        uid = auth.user(t)
        if not uid:
            raise HTTPException(401, "Phiên đã hết hạn; đăng nhập lại.")
        return uid

    def store(uid: str = Depends(user)):
        return FinanceStore(root / f"{uid}.sqlite3")

    def key(idempotency_key: Annotated[str, Header()]):
        return idempotency_key

    def session(sid):
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", sid):
            raise ValueError("Mã phiên không hợp lệ.")
        return sid

    def engine(s, sid):
        return ChatEngine(s, session(sid), today=today(), classifier=TeamClassifier())

    def snapshot(e, message=None):
        return {**e.snapshot(), "message": message, "overview": e.store.overview(today().strftime("%Y-%m"))}

    @app.get("/api/health")
    def health():
        return {"status": "ok", "version": "2.0.0", "today": today().isoformat()}

    @app.post("/api/auth/register")
    def register(body: Credentials):
        return auth.register(body.email, body.password)

    @app.post("/api/auth/login")
    def login(body: Credentials):
        return auth.login(body.email, body.password)

    @app.post("/api/auth/logout")
    def logout(t: str = Depends(token), uid: str = Depends(user)):
        auth.logout(t)
        return {"ok": True}

    @app.post("/api/auth/change-password")
    def change_password(body: PasswordChange, uid: str = Depends(user)):
        auth.change(uid, body.old_password, body.new_password)
        return {"ok": True, "message": "Đã đổi mật khẩu. Đăng nhập lại trên các thiết bị."}

    @app.get("/api/categories")
    def categories(uid: str = Depends(user)):
        return CATEGORIES

    @app.get("/api/chat/{sid}")
    def chat_get(sid: str, s: FinanceStore = Depends(store)):
        with s.atomic():
            return snapshot(engine(s, sid))

    @app.post("/api/chat/{sid}")
    def chat_action(sid: str, body: ChatAction, k: str = Depends(key), s: FinanceStore = Depends(store)):
        e = engine(s, sid)
        def apply():
            if body.action == "send":
                message = e.send(body.text)
            elif body.action == "edit":
                message = e.edit(body.draft_id, body.version, body.fields)
            elif body.action == "confirm":
                message = e.confirm(body.draft_id, body.version)
            elif body.action == "cancel":
                current = e.snapshot()["draft"]
                if not current or (current["id"], current["version"]) != (body.draft_id, body.version):
                    raise ValueError("Bản nháp đã thay đổi; tải lại trước khi hủy.")
                message = e.send("hủy")
            else:
                message = e.retry()
            return snapshot(e, message)
        return s.once(k, [sid, body.model_dump()], apply)

    @app.post("/api/chat/{sid}/receipt")
    def receipt(sid: str, file: UploadFile = File(...), k: str = Depends(key), s: FinanceStore = Depends(store)):
        e = engine(s, sid)
        content = file.file.read(10 * 1024 * 1024 + 1)
        if len(content) > 10 * 1024 * 1024:
            raise ValueError("Tệp tối đa 10 MB.")
        digest = hashlib.sha256(content).hexdigest()
        cached = s.cached(k, [sid, "receipt", digest])
        if cached is not None:
            return cached
        # OCR chạy ngoài transaction khóa ghi; tạo nháp và cache phản hồi nguyên tử sau đó.
        text = extract_text(content)
        fields, issues = receipt_fields(text, today())
        def apply():
            state = e.store.load_session(sid)
            if state["draft"] or state["pending"]:
                raise ValueError("Hãy hoàn tất hoặc hủy bản nháp hiện tại trước khi gửi hóa đơn.")
            draft = Draft(intent="record_expense", fields=fields, issues=issues).to_dict()
            state["draft"] = draft
            state["history"].append({"role": "user", "content": "[Ảnh/PDF hóa đơn]"})
            message = e.evaluate(state)
            return {**snapshot(e, message), "ocr_text": text[:12000], "ocr_note": "OCR có thể sai, đặc biệt dấu tiếng Việt. Kiểm tra tiền/ngày/danh mục trước xác nhận."}
        return s.once(k, [sid, "receipt", digest], apply)

    @app.get("/api/transactions")
    def transactions(month: str, s: FinanceStore = Depends(store)):
        month_check(month)
        with s.connection() as db:
            return [dict(r) for r in db.execute("SELECT * FROM expenses WHERE substr(date,1,7)=? ORDER BY date DESC,id", (month,))]

    @app.post("/api/transactions")
    def create_expense(body: Expense, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["create_expense", body.model_dump()], lambda: s.commit(k, "record_expense", body.model_dump(), today()))

    @app.put("/api/transactions/{rid}")
    def update_expense(rid: str, body: Expense, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["edit", rid, body.model_dump()], lambda: s.update_expense(rid, body.model_dump(), today()))

    @app.delete("/api/transactions/{rid}")
    def delete_expense(rid: str, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["delete", rid], lambda: s.update_expense(rid, {}, today(), delete=True))

    @app.get("/api/summary")
    def summary(month: str, s: FinanceStore = Depends(store)):
        with s.atomic():
            return s.overview(month_check(month))

    @app.get("/api/budgets/{month}")
    def budget(month: str, s: FinanceStore = Depends(store)):
        return s.budget(month)

    @app.put("/api/budgets/{month}")
    def save_budget(month: str, body: Budget, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["budget", month, body.model_dump()], lambda: s.set_budget(month, body.total, body.limits, body.threshold))

    @app.get("/api/budgets/{month}/suggestions")
    def suggestions(month: str, s: FinanceStore = Depends(store)):
        with s.atomic():
            return s.suggest(month, today())

    @app.get("/api/bills")
    def bills(s: FinanceStore = Depends(store)):
        with s.atomic() as db:
            schedules = [dict(r) for r in db.execute("SELECT * FROM bills ORDER BY name")]
            return {"schedules": schedules, "periods": s.periods(today()), "reminders": s.reminders(today()), "obligations": s.obligations(today())}

    @app.post("/api/bills")
    def create_bill(body: Bill, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["bill", body.model_dump()], lambda: s.commit(k, "create_recurring_bill", body.model_dump(), today()))

    @app.post("/api/bills/{bill_id}/pay")
    def pay(bill_id: str, body: Payment, k: str = Depends(key), s: FinanceStore = Depends(store)):
        return s.once(k, ["pay", bill_id, body.model_dump()], lambda: s.pay(bill_id, body.month, body.paid_on, body.expense_id, today()))

    return app


app = create_app()
