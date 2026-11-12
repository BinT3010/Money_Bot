"""Lịch hóa đơn: xem kỳ/nhắc/đối chiếu, tạo lịch, xác nhận thanh toán."""
from fastapi import Depends
from pydantic import Field, StrictInt

from finance import FinanceStore
from routes import StrictModel


class Bill(StrictModel):
    name: str = Field(max_length=300)
    amount_vnd: StrictInt
    due_day: StrictInt
    start_month: str


class Payment(StrictModel):
    month: str
    paid_on: str
    expense_id: str | None = None


def register(app, d):
    @app.get("/api/bills")
    def bills(s: FinanceStore = Depends(d.store)):
        today = d.today()
        with s.atomic() as db:
            schedules = [dict(r) for r in db.execute("SELECT * FROM bills ORDER BY name")]
            return {"schedules": schedules, "periods": s.periods(today), "reminders": s.reminders(today), "obligations": s.obligations(today)}

    @app.post("/api/bills")
    def create_bill(body: Bill, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["bill", body.model_dump()], lambda: s.commit(k, "create_recurring_bill", body.model_dump(), d.today()))

    @app.post("/api/bills/{bill_id}/pay")
    def pay(bill_id: str, body: Payment, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["pay", bill_id, body.model_dump()], lambda: s.pay(bill_id, body.month, body.paid_on, body.expense_id, d.today()))
