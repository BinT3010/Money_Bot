"""API chung cho ứng dụng Java. Chạy một hoặc nhiều worker: SQLite khóa transaction.
Mỗi người dùng có DB riêng, server lấy đường dẫn từ token chứ không nhận từ client.

File này chỉ giữ khung: phụ thuộc chung (xác thực, kho, khóa chống lặp), xử lý lỗi, health.
Route theo miền nằm trong routes/{auth,chat,transactions,budget,bills}.py.
"""
import os
import re
from pathlib import Path
from dataclasses import dataclass
from typing import Callable
from typing import Annotated

from fastapi import FastAPI, Depends, Header, HTTPException
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from moneybot.auth import Auth
from moneybot.finance import FinanceStore
from moneybot.classification import TeamClassifier
from moneybot.money_chat.engine import ChatEngine
from moneybot.money_chat.models import local_today
from moneybot.routes import auth as auth_routes, chat, transactions, budget, bills


@dataclass(frozen=True)
class RouteDependencies:
    auth: Auth
    today: Callable
    token: Callable
    user: Callable
    store: Callable
    key: Callable
    engine: Callable
    snapshot: Callable


def create_app(data_dir=None, today_provider=None):
    root = Path(data_dir or os.getenv("MONEYBOT_DATA", Path(__file__).resolve().parents[1] / "data")).resolve()
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

    deps = RouteDependencies(auth=auth, today=today, token=token, user=user, store=store, key=key,
                           engine=engine, snapshot=snapshot)
    for module in (auth_routes, chat, transactions, budget, bills):
        module.register(app, deps)
    return app


app = create_app()
