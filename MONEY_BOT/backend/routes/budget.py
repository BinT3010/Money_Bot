"""Thống kê tháng, ngân sách, hạn mức và gợi ý hạn mức."""
from fastapi import Depends
from pydantic import StrictInt

from finance import FinanceStore, month_check
from routes import StrictModel


class Budget(StrictModel):
    total: StrictInt
    limits: dict[str, StrictInt]
    threshold: StrictInt = 80


def register(app, d):
    @app.get("/api/summary")
    def summary(month: str, s: FinanceStore = Depends(d.store)):
        with s.atomic():
            return s.overview(month_check(month))

    @app.get("/api/budgets/{month}")
    def budget(month: str, s: FinanceStore = Depends(d.store)):
        return s.budget(month)

    @app.put("/api/budgets/{month}")
    def save_budget(month: str, body: Budget, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        return s.once(k, ["budget", month, body.model_dump()], lambda: s.set_budget(month, body.total, body.limits, body.threshold))

    @app.get("/api/budgets/{month}/suggestions")
    def suggestions(month: str, s: FinanceStore = Depends(d.store)):
        with s.atomic():
            return s.suggest(month, d.today())
