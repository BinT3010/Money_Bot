"""Lịch hóa đơn: xem kỳ/nhắc/đối chiếu, tạo, sửa, ngừng lịch, xác nhận thanh toán."""
from typing import Literal

from fastapi import Depends
from pydantic import Field, StrictInt

from finance import FinanceStore
from routes import StrictModel


class Bill(StrictModel):
    name: str = Field(max_length=300)
    amount_vnd: StrictInt
    due_day: StrictInt
    start_month: str


class BillUpdate(Bill):
    # null = lịch đang chạy (dùng để tiếp tục lịch đã ngừng); yyyy-MM = kỳ cuối cùng.
    end_month: str | None = None


class Payment(StrictModel):
    month: str
    paid_on: str
    expense_id: str | None = None
    # Số tiền thực trả; bỏ trống = số tiền dự kiến của lịch. Khi liên kết khoản chi, phải bằng tiền khoản chi đó.
    amount_vnd: StrictInt | None = None

    def fingerprint(self):
        # Yêu cầu không gửi amount_vnd giữ dấu vân tay chống lặp như trước khi có trường này.
        return self.model_dump(exclude={"amount_vnd"} if self.amount_vnd is None else set())


def register(app, d):
    @app.get("/api/bills")
    def bills(limit: int = 100, offset: int = 0, history: Literal["recent", "all"] = "recent",
              s: FinanceStore = Depends(d.store)):
        today = d.today()
        with s.atomic() as db:
            schedules = [dict(r) for r in db.execute("SELECT * FROM bills ORDER BY name")]
            return {"schedules": schedules, **s.period_page(today, limit, offset, history),
                    "reminders": s.reminders(today), "obligations": s.obligations(today)}

    @app.post("/api/bills")
    def create_bill(body: Bill, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["bill", body.model_dump()], lambda: s.commit(k, "create_recurring_bill", body.model_dump(), d.today()))

    @app.put("/api/bills/{bill_id}")
    def update_bill(bill_id: str, body: BillUpdate, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["update_bill", bill_id, body.model_dump()], lambda: s.update_bill(bill_id, body.model_dump(), d.today()))

    @app.delete("/api/bills/{bill_id}")
    def stop_bill(bill_id: str, end_month: str | None = None, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["stop_bill", bill_id, end_month], lambda: s.stop_bill(bill_id, d.today(), end_month))

    @app.post("/api/bills/{bill_id}/pay")
    def pay(bill_id: str, body: Payment, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["pay", bill_id, body.fingerprint()],
                      lambda: s.pay(bill_id, body.month, body.paid_on, body.expense_id, d.today(), body.amount_vnd))
