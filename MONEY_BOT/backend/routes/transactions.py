"""Khoản chi: liệt kê theo tháng, thêm, sửa, xóa."""
from fastapi import Depends
from pydantic import Field, StrictInt

from finance import FinanceStore, month_check
from routes import StrictModel


class Expense(StrictModel):
    amount_vnd: StrictInt
    description: str = Field(max_length=300)
    category_id: str
    date: str


def register(app, d):
    @app.get("/api/transactions")
    def transactions(month: str, s: FinanceStore = Depends(d.store)):
        month_check(month)
        with s.connection() as db:
            return [dict(r) for r in db.execute("SELECT * FROM expenses WHERE substr(date,1,7)=? ORDER BY date DESC,id", (month,))]

    @app.post("/api/transactions")
    def create_expense(body: Expense, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["create_expense", body.model_dump()], lambda: s.commit(k, "record_expense", body.model_dump(), d.today()))

    @app.put("/api/transactions/{rid}")
    def update_expense(rid: str, body: Expense, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["edit", rid, body.model_dump()], lambda: s.update_expense(rid, body.model_dump(), d.today()))

    @app.delete("/api/transactions/{rid}")
    def delete_expense(rid: str, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["delete", rid], lambda: s.update_expense(rid, {}, d.today(), delete=True))
