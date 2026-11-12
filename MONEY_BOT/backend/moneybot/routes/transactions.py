"""Khoản chi: liệt kê theo tháng, thêm, sửa, xóa."""
from fastapi import Depends
from pydantic import Field, StrictInt

from moneybot.finance import FinanceStore, month_check
from moneybot.routes import StrictModel


class Expense(StrictModel):
    amount_vnd: StrictInt
    description: str = Field(max_length=300)
    category_id: str
    date: str
    # Tùy chọn. Bỏ qua (hoặc null) khi sửa = giữ cửa hàng đã lưu; "" = xóa.
    merchant: str | None = Field(default=None, max_length=120)

    def fields(self):
        # Bỏ trường None để yêu cầu không có cửa hàng giữ nguyên dấu vân tay chống lặp như trước.
        return self.model_dump(exclude_none=True)


def register(app, d):
    @app.get("/api/transactions")
    def transactions(month: str, s: FinanceStore = Depends(d.store)):
        month_check(month)
        with s.connection() as db:
            return [dict(r) for r in db.execute("SELECT * FROM expenses WHERE substr(date,1,7)=? ORDER BY date DESC,id", (month,))]

    @app.post("/api/transactions")
    def create_expense(body: Expense, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["create_expense", body.fields()], lambda: s.commit(k, "record_expense", body.fields(), d.today()))

    @app.put("/api/transactions/{rid}")
    def update_expense(rid: str, body: Expense, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["edit", rid, body.fields()], lambda: s.update_expense(rid, body.fields(), d.today()))

    @app.delete("/api/transactions/{rid}")
    def delete_expense(rid: str, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["delete", rid], lambda: s.update_expense(rid, {}, d.today(), delete=True))
