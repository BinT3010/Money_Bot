"""Tài khoản: đăng ký, đăng nhập, đăng xuất, đổi mật khẩu, danh mục."""
from fastapi import Depends
from pydantic import Field

from moneybot.money_chat.models import CATEGORIES
from moneybot.routes import StrictModel


class Credentials(StrictModel):
    email: str = Field(max_length=254)
    password: str = Field(min_length=1, max_length=128)


class PasswordChange(StrictModel):
    old_password: str = Field(max_length=128)
    new_password: str = Field(max_length=128)


def register(app, d):
    @app.post("/api/auth/register")
    def register_account(body: Credentials):
        return d.auth.register(body.email, body.password)

    @app.post("/api/auth/login")
    def login(body: Credentials):
        return d.auth.login(body.email, body.password)

    @app.post("/api/auth/logout")
    def logout(t: str = Depends(d.token), uid: str = Depends(d.user)):
        d.auth.logout(t)
        return {"ok": True}

    @app.post("/api/auth/change-password")
    def change_password(body: PasswordChange, uid: str = Depends(d.user)):
        d.auth.change(uid, body.old_password, body.new_password)
        return {"ok": True, "message": "Đã đổi mật khẩu. Đăng nhập lại trên các thiết bị."}

    @app.get("/api/categories")
    def categories(uid: str = Depends(d.user)):
        return CATEGORIES
