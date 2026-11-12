"""P01–P06: bộ hiểu câu bằng quy tắc, có giới hạn được ghi trong README."""
import re
import unicodedata
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from .models import Parsed, CATEGORIES


# P01: bỏ dấu để nhận mẫu; nội dung lưu vẫn giữ nguyên tiếng Việt.
def fold(text):
    text = unicodedata.normalize("NFD", text.lower()).replace("đ", "d")
    return "".join(c for c in text if unicodedata.category(c) != "Mn")


MONEY = re.compile(r"(?<![\w/])(-?\d+(?:[.,]\d+)*)\s*(triệu|trieu|tr|nghìn|nghin|ngàn|ngan|k|đồng|dong|vnđ|vnd|đ)(?!\w)", re.I)
DATE = re.compile(r"\b(\d{1,2})/(\d{1,2})/(\d{4})\b")
MONTH = re.compile(r"(?:tháng|thang)\s*(\d{1,2})(?:\s*[/\-]\s*|\s+năm\s+|\s+nam\s+)(\d{4})", re.I)


MERCHANT = re.compile(r"(?:^|[;\n])\s*(?:cửa hàng|cua hang|merchant|store)\s*:\s*([^;\n]*)", re.I)


# P02: Decimal tránh sai số tiền do float; đơn vị không rõ thì hỏi.
def parse_money(text):
    matches = list(MONEY.finditer(text))
    if not matches:
        return None, None, []
    raw, unit = matches[0].group(1, 2)
    unit = fold(unit)
    multiplier = 10**6 if unit in {"trieu", "tr"} else 1000 if unit in {"k", "nghin", "ngan"} else 1
    if re.fullmatch(r"-?\d{1,3}(?:[.,]\d{3})+", raw):
        raw = raw.replace(".", "").replace(",", "")
    else:
        raw = raw.replace(",", ".")
    try:
        amount = Decimal(raw) * multiplier
        if amount != amount.to_integral_value() or not 0 < amount <= 10**12:
            raise InvalidOperation()
        return int(amount), None, matches
    except InvalidOperation:
        return None, "Số tiền phải là số nguyên dương, tối đa 1.000 tỷ đồng.", matches


def parse_date(text, today):
    match = DATE.search(text)
    if match:
        try:
            d, m, y = map(int, match.groups())
            value = date(y, m, d)
            if value > today:
                raise ValueError()
            return value.isoformat(), None
        except ValueError:
            return None, "Ngày không tồn tại hoặc ở tương lai. Nhập lại dd/mm/yyyy."
    if "hom qua" in fold(text):
        return (today - timedelta(days=1)).isoformat(), None
    if "hom nay" in fold(text):
        return today.isoformat(), None
    if re.search(r"\bngay\s+\d", fold(text)):
        return None, "Vui lòng ghi đầy đủ ngày dd/mm/yyyy."
    return None, None


# P03: chỉ chọn ý định; thao tác xác nhận/hủy được engine xử lý riêng.
def detect_intent(text):
    text = MERCHANT.sub("", text).strip()
    t = fold(text)
    if "da tra" in t or "da thanh toan" in t:
        return "payment_unsupported"
    if any(s in t for s in ["laptop tot", "tu van", "thoi tiet", "bai tho"]):
        return "unsupported"
    if any(s in t for s in ["sap phai dong", "sap den han", "hoa don can tra", "khoan can tra", "xem hoa don"]):
        return "list_due_bills"
    if any(s in t for s in ["moi thang", "hang thang", "tao lich", "tao hoa don", "nhac tien", "han ngay"]):
        return "create_recurring_bill"
    if ("thang" in t and any(s in t for s in ["chi bao nhieu", "chi tieu", "tong", "rieng"])) or "thong ke" in t:
        return "query_spending"
    if MONEY.search(text) or any(s in t for s in ["an trua", "an sang", "ca phe", "mua ", "do xang", "gui xe", "ve xe", "ghi chi"]):
        return "record_expense"
    return None


# P04: trích tháng, giữ tháng thiếu năm để hỏi ở lượt sau.
def month_fields(text, today, current, target):
    match = MONTH.search(text)
    if match:
        return {target: f"{int(match[2]):04d}-{int(match[1]):02d}"}
    t = fold(text)
    if "thang nay" in t:
        return {target: today.strftime("%Y-%m")}
    if "thang truoc" in t:
        return {target: (today.replace(day=1) - timedelta(days=1)).strftime("%Y-%m")}
    if re.fullmatch(r"\d{4}", text.strip()) and current.get("month_number"):
        return {target: f"{text.strip()}-{current['month_number']:02d}"}
    match = re.search(r"thang\s+(\d{1,2})(?!\d)", t)
    if match:
        return {"month_number": int(match[1]), target: None}
    return {}


# P05: bỏ các đoạn đã trích khỏi nội dung; không dùng câu trả lời số làm tên.
def description_from(text, intent):
    clean = MONEY.sub("", text)
    clean = DATE.sub("", clean)
    clean = MONTH.sub("", clean)
    clean = re.sub(r"(?i)(hôm qua|hôm nay|hom qua|hom nay)", "", clean)
    if intent == "create_recurring_bill":
        clean = re.sub(r"(?i)(hạn\s+)?ngày\s+\d+", "", clean)
        clean = re.sub(r"(?i)(mỗi tháng|hàng tháng|bắt đầu|từ tháng|từ|nhắc|tạo hóa đơn|tạo hoá đơn|tạo lịch|tạo)", "", clean)
    clean = re.sub(r"(?i)^\s*(sửa thành|đổi thành|sửa nội dung thành|ghi chi|ghi lại|ghi|chi|sửa|đổi)\s*", "", clean)
    clean = re.sub(r"(?i)\bngày\b", "", clean)
    # Giữ nội dung khi tiền thiếu đơn vị: “Cà phê 45” -> “Cà phê”.
    clean = re.sub(r"\s*-?\d+(?:[.,]\d+)*\s*$", "", clean)
    clean = re.sub(r"\s+", " ", clean).strip(" ,.;")
    if not clean or not re.search(r"[^\W\d_]", clean, re.UNICODE) or fold(clean) in {"thanh", "tien", "so tien"}:
        return None
    return clean


# P06: đầu vào có draft hiện tại để bổ sung trường thay vì tạo giao dịch mới.
class RuleInterpreter:
    name = "Quy tắc (không gọi AI)"

    def parse(self, text, today, intent=None, current=None):
        current = current or {}
        merchant_matches = list(MERCHANT.finditer(text))
        text = MERCHANT.sub("", text).strip()
        kind = intent or detect_intent(text)
        out = Parsed(intent=kind)
        if kind == "record_expense" and merchant_matches:
            names = {match.group(1).strip() for match in merchant_matches}
            if len(names) == 1:
                out.fields["merchant"] = names.pop()
            else:
                out.issues["merchant"] = "Có nhiều cửa hàng; hãy chọn một tên trong biểu mẫu."
        amount, error, matches = parse_money(text)
        out.multiple = len(matches) > 1
        if out.multiple:
            return out
        if kind in {"record_expense", "create_recurring_bill"}:
            if matches:
                out.fields["amount_vnd"] = amount
                if error:
                    out.issues["amount_vnd"] = error
            elif re.search(r"\d", text) and not DATE.search(text) and not MONTH.search(text) and not re.search(r"ngay\s+\d", fold(text)):
                out.issues["amount_vnd"] = "Số tiền chưa rõ đơn vị. Hãy nhập như 45k hoặc 45 đồng."
            name = "description" if kind == "record_expense" else "name"
            content = description_from(text, kind)
            # Câu chỉ chọn danh mục không được thay nội dung giao dịch.
            category = next((k for k, v in CATEGORIES.items() if fold(text).strip() in {k, fold(v), "danh muc " + fold(v)}), None)
            if category and kind == "record_expense":
                out.fields.update(category_id=category, category_source="user")
            elif content:
                out.fields[name] = content
            if kind == "record_expense":
                value, error = parse_date(text, today)
                if value or error:
                    out.fields["date"] = value
                    out.fields["date_source"] = "user"
                if error:
                    out.issues["date"] = error
            else:
                match = re.search(r"ngay\s+(\d+)", fold(text))
                if match:
                    out.fields["due_day"] = int(match[1])
                out.fields.update(month_fields(text, today, current, "start_month"))
        elif kind == "query_spending":
            out.fields.update(month_fields(text, today, current, "month"))
            for key, label in CATEGORIES.items():
                # "Tiêu dùng/Mua sắm": khớp từng phần của nhãn ghép.
                if any(fold(part) in fold(text) for part in label.split("/")):
                    out.fields["category_id"] = key
        return out
