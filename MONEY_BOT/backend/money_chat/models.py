"""M01–M04: dữ liệu chung và kiểm tra trước khi ghi."""
from dataclasses import dataclass, field, asdict
from datetime import date, datetime, timezone, timedelta
from uuid import uuid4

# M01: mã ổn định cho chương trình, nhãn tiếng Việt cho người dùng.
CATEGORIES = {
    "food": "Ăn uống", "shopping": "Tiêu dùng/Mua sắm",
    "transport": "Đi lại", "utilities": "Hóa đơn tiện ích",
    "entertainment": "Giải trí", "other": "Khác",
}
WRITE_INTENTS = {"record_expense", "create_recurring_bill"}
INTENTS = WRITE_INTENTS | {"query_spending", "list_due_bills"}


def local_today():
    # Việt Nam UTC+7; không phụ thuộc múi giờ Windows hoặc gói tzdata.
    return datetime.now(timezone(timedelta(hours=7))).date()


def money(value):
    return f"{value:,}".replace(",", ".") + "đ"


# M02: kết quả hiểu câu chỉ là dữ liệu, không có quyền ghi database.
@dataclass
class Parsed:
    intent: str | None = None
    fields: dict = field(default_factory=dict)
    issues: dict = field(default_factory=dict)
    multiple: bool = False


# M03: một bản nháp có ID và version để từ chối thao tác đã lỗi thời.
@dataclass
class Draft:
    intent: str
    id: str = field(default_factory=lambda: uuid4().hex)
    version: int = 1
    fields: dict = field(default_factory=dict)
    issues: dict = field(default_factory=dict)

    def to_dict(self):
        return asdict(self)


# M04: kiểm tra lại ở engine VÀ nơi ghi; không tin dữ liệu từ UI/AI.
def validate(intent, fields, today):
    errors = {}
    if intent in WRITE_INTENTS:
        amount = fields.get("amount_vnd")
        if type(amount) is not int or not 0 < amount <= 10**12:
            errors["amount_vnd"] = "Nhập số tiền nguyên dương (tối đa 1.000 tỷ đồng)."
        name = "description" if intent == "record_expense" else "name"
        if not isinstance(fields.get(name), str) or not fields[name].strip():
            errors[name] = "Khoản tiền này dùng cho việc gì?" if name == "description" else "Tên hóa đơn là gì?"
        elif len(fields[name]) > 300:
            errors[name] = "Nội dung tối đa 300 ký tự."
    if intent == "record_expense":
        try:
            d = date.fromisoformat(fields.get("date", ""))
            # Cùng mốc năm 2000 với tháng bắt đầu hóa đơn.
            if d > today or d < date(2000, 1, 1) or d.isoformat() != fields.get("date"):
                raise ValueError()
        except (ValueError, TypeError):
            errors["date"] = "Nhập ngày hợp lệ từ năm 2000, không ở tương lai (dd/mm/yyyy)."
        if fields.get("category_id") not in CATEGORIES:
            errors["category_id"] = "Hãy chọn một danh mục cho khoản chi."
        # Cửa hàng tùy chọn: chỉ hiển thị, không dùng để suy ra danh mục.
        merchant = fields.get("merchant")
        if merchant is not None and (not isinstance(merchant, str) or len(merchant) > 120):
            errors["merchant"] = "Tên cửa hàng tối đa 120 ký tự."
    if intent == "create_recurring_bill":
        if type(fields.get("due_day")) is not int or not 1 <= fields["due_day"] <= 31:
            errors["due_day"] = "Ngày lặp phải từ 1 đến 31."
        if not valid_month(fields.get("start_month")):
            errors["start_month"] = "Bắt đầu từ tháng/năm nào? Ví dụ: tháng 12/2026."
        elif not "2000-01" <= fields["start_month"] <= f"{today.year + 10:04d}-12":
            errors["start_month"] = "Tháng bắt đầu phải từ năm 2000 đến tối đa 10 năm tới."
    if intent == "query_spending" and not valid_month(fields.get("month")):
        errors["month"] = "Bạn muốn xem tháng/năm nào? Ví dụ: tháng 11/2026."
    return errors


def valid_month(value):
    try:
        return isinstance(value, str) and len(value) == 7 and date.fromisoformat(value + "-01").strftime("%Y-%m") == value
    except ValueError:
        return False
