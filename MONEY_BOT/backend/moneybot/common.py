"""Hàm kiểm tra dùng chung cho finance/budget/bills; tách riêng để các mixin không import vòng."""
from moneybot.money_chat.models import valid_month


def month_check(month):
    if not valid_month(month):
        raise ValueError("Tháng phải có dạng yyyy-MM.")
    return month


def shift_month(month, offset):
    y, m = map(int, month_check(month).split("-"))
    n = y * 12 + m - 1 + offset
    return f"{n // 12:04d}-{n % 12 + 1:02d}"


def integer(value, minimum=0, maximum=10**12):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"Giá trị phải là số nguyên từ {minimum} đến {maximum}.")
    return value
