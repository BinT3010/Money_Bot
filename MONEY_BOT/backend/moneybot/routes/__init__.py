"""Route theo miền. Mỗi module có register(app, deps); deps do app.create_app tạo (xác thực, kho, khóa chống lặp)."""
from pydantic import BaseModel, ConfigDict


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")
