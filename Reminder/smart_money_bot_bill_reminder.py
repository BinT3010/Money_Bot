import calendar
import re
from datetime import date

try:
    from classifier import classify, _fold
except ImportError:
    classify = None
    _fold = lambda s: (s or "").lower()

_RE_DUE_DAY = re.compile(
    r"(?:han\s*(?:ngay|vano\s*ngay)?|ngay\s*lap|hang\s*thang\s*ngay|ngay)\s*(?P<day>\d{1,2})(?!\s*[/-]\s*\d)"
)
_RE_START_MONTH = re.compile(
    r"(?:bat\s*dau\s*(?:tu\s*)?(?:thang\s*)?|tu\s*thang\s*|thang\s*)(?P<m>\d{1,2})\s*[/-]\s*(?P<y>\d{4})"
)
_RE_YYYY_MM = re.compile(r"^\d{4}-(?:0[1-9]|1[0-2])$")


# --------------------------------------------------------------------------
# 1. Tách câu tạo lịch hóa đơn định kỳ & kiểm tra bản nháp
# --------------------------------------------------------------------------
def tach_lich_hoa_don(text, today=None):
    """
    Nhận câu tiếng Việt (vd: "Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026")
    và trả về bản nháp tạo lịch hóa đơn định kỳ tương thích với app.py & classifier.py.
    """
    today = today or date.today()
    raw = (text or "").strip()
    folded = _fold(raw)

    # Trích xuất ngày lặp hàng tháng (1..31)
    due_day = None
    m_day = _RE_DUE_DAY.search(folded)
    if m_day:
        d_val = int(m_day.group("day"))
        if 1 <= d_val <= 31:
            due_day = d_val

    # Trích xuất tháng bắt đầu (YYYY-MM), mặc định là tháng hiện tại nếu câu không nêu
    start_month = today.strftime("%Y-%m")
    start_month_default = True
    m_month = _RE_START_MONTH.search(folded)
    if m_month:
        mo, yr = int(m_month.group("m")), int(m_month.group("y"))
        if 1 <= mo <= 12:
            start_month = f"{yr:04d}-{mo:02d}"
            start_month_default = False

    # Tận dụng classifier.py để lấy số tiền và làm sạch nội dung tên hóa đơn
    clean_text = raw
    for match in (m_month, m_day):
        if match:
            s, e = match.span()
            clean_text = clean_text[:s] + " " * (e - s) + clean_text[e:]
    clean_text = re.sub(
        r"(?i)\b(mỗi\s*tháng|hàng\s*tháng|hằng\s*tháng|định\s*kỳ|hạn\s*ngày|bắt\s*đầu)\b",
        " ",
        clean_text,
    )

    if classify is not None:
        parsed = classify(clean_text, today=today)
        amount = parsed["so_tien"]
        name = parsed["noi_dung"]
    else:
        amount = None
        name = clean_text.strip(" -,.:;")

    record = {
        "name": name,
        "amount_vnd": amount,
        "due_day": due_day,
        "start_month": start_month,
        "start_month_mac_dinh": start_month_default,
        "danh_muc": "Hóa đơn tiện ích",
        "paid_months": [],
    }
    return kiem_tra_hoa_don(record)


def kiem_tra_hoa_don(record):
    """
    Kiểm tra lại bản ghi hóa đơn định kỳ sau khi người dùng sửa trên form (app.py).
    Đồng bộ cả key tiếng Anh (app.py) và tiếng Việt (classifier.py).
    """
    name = str(record.get("name") or record.get("ten") or "").strip()
    amount = record.get("amount_vnd") if "amount_vnd" in record else record.get("so_tien")
    due_day = record.get("due_day") if "due_day" in record else record.get("ngay_lap")
    start_month = str(record.get("start_month") or record.get("thang_bat_dau") or "").strip()

    missing = []
    if not name:
        missing.append("name")
    if not isinstance(amount, int) or isinstance(amount, bool) or amount <= 0:
        missing.append("amount_vnd")
    if not isinstance(due_day, int) or isinstance(due_day, bool) or not (1 <= due_day <= 31):
        missing.append("due_day")
    if not _RE_YYYY_MM.match(start_month):
        missing.append("start_month")

    record["name"] = name
    record["ten"] = name
    record["amount_vnd"] = amount
    record["so_tien"] = amount
    record["due_day"] = due_day
    record["ngay_lap"] = due_day
    record["start_month"] = start_month
    record["danh_muc"] = "Hóa đơn tiện ích"
    record["truong_thieu"] = missing
    record["trang_thai"] = "can_bo_sung" if missing else "cho_xac_nhan"
    return record


# --------------------------------------------------------------------------
# 2. Tính ngày đến hạn thực tế & kiểm tra nhắc hạn (trước 3 ngày, 1 ngày, hôm nay, quá hạn)
# --------------------------------------------------------------------------
def ngay_han_thuc_te(year, month, due_day):
    """Lấy ngày đến hạn trong tháng; nếu tháng ngắn (28/29/30 ngày) thì lấy ngày cuối tháng."""
    last_day = calendar.monthrange(year, month)[1]
    return date(year, month, min(int(due_day), last_day))


def kiem_tra_nhac_han(bill, today=None):
    """
    Đánh giá trạng thái hạn đóng tiền của một hóa đơn tại ngày today.
    Trả về dict chứa trạng thái, số ngày còn lại, cờ can_nhac và câu thông báo.
    """
    today = today or date.today()
    cycle_month = today.strftime("%Y-%m")

    name = bill.get("name") or bill.get("ten") or "Hóa đơn"
    amount = int(bill.get("amount_vnd") or bill.get("so_tien") or 0)
    due_day = int(bill.get("due_day") or bill.get("ngay_lap") or 1)
    start_month = bill.get("start_month") or cycle_month
    paid_months = set(bill.get("paid_months") or [])
    is_paid = bool(bill.get("is_paid") or bill.get("da_tra") or (cycle_month in paid_months))

    due_date = ngay_han_thuc_te(today.year, today.month, due_day)
    days_left = (due_date - today).days

    if cycle_month < start_month:
        status, label, notify, msg = "chua_bat_dau", f"Bắt đầu từ {start_month}", False, None
    elif is_paid:
        status, label, notify, msg = "da_thanh_toan", "Đã thanh toán", False, None
    elif days_left < 0:
        status = "qua_han"
        label = f"Quá hạn {-days_left} ngày"
        notify = True
        msg = f"Quá hạn {-days_left} ngày: {name} ({amount:,}đ) - hạn {due_date.strftime('%d/%m/%Y')}".replace(",", ".")
    elif days_left == 0:
        status = "den_han_hom_nay"
        label = "Đến hạn hôm nay"
        notify = True
        msg = f"Đến hạn hôm nay: {name} ({amount:,}đ) - hạn {due_date.strftime('%d/%m/%Y')}".replace(",", ".")
    elif days_left in (1, 3):
        status = f"nhac_truoc_{days_left}_ngay"
        label = f"Sắp đến hạn (còn {days_left} ngày)"
        notify = True
        msg = f"Nhắc trước {days_left} ngày: {name} ({amount:,}đ) - hạn {due_date.strftime('%d/%m/%Y')}".replace(",", ".")
    elif 1 < days_left <= 7:
        status, label, notify, msg = "sap_den_han", f"Sắp đến hạn (còn {days_left} ngày)", False, None
    else:
        status, label, notify, msg = "chua_den_han", f"Chưa đến hạn (còn {days_left} ngày)", False, None

    return {
        "name": name,
        "amount_vnd": amount,
        "due_day": due_day,
        "due_date": due_date.isoformat(),
        "days_left": days_left,
        "status": status,
        "trang_thai_hien_thi": label,
        "can_nhac": notify,
        "thong_bao": msg,
    }


def due_bills(bills, today=None):
    """
    Trả về danh sách hóa đơn cần trả & thông báo nhắc nhở.
    Cấu trúc {"items": [...], "reminders": [...], "total_due_vnd": int}
    tương thích trực tiếp với lệnh store.due_bills(today)["items"] trong app.py.
    """
    today = today or date.today()
    items = []
    reminders = []
    total_due = 0

    for bill in bills:
        info = kiem_tra_nhac_han(bill, today=today)
        if info["status"] == "chua_bat_dau":
            continue
        items.append(info)
        if info["status"] != "da_thanh_toan":
            total_due += info["amount_vnd"]
        if info["can_nhac"]:
            reminders.append(info["thong_bao"])

    return {
        "items": items,
        "reminders": reminders,
        "total_due_vnd": total_due,
    }


# --------------------------------------------------------------------------
# 3. Xác nhận thanh toán hóa đơn (chống ghi trùng & tạo bản ghi chi tiêu)
# --------------------------------------------------------------------------
def xac_nhan_thanh_toan(bill, today=None, paid_amount=None):
    """
    Đánh dấu kỳ hóa đơn tháng hiện tại đã thanh toán và trả về bản ghi khoản chi
    chuẩn format của classifier.py để lưu vào lịch sử giao dịch đúng 1 lần.
    Trả về (thanh_cong: bool, ban_ghi_giao_dich_hoac_None, thong_bao: str).
    """
    today = today or date.today()
    cycle_month = today.strftime("%Y-%m")
    paid_months = list(bill.get("paid_months") or [])

    if bill.get("is_paid") or bill.get("da_tra") or (cycle_month in paid_months):
        return False, None, f"Hóa đơn '{bill.get('name')}' kỳ {cycle_month} đã thanh toán, không tạo chi trùng."

    paid_months.append(cycle_month)
    bill["paid_months"] = paid_months
    bill["da_tra"] = True

    amount = int(paid_amount or bill.get("amount_vnd") or bill.get("so_tien") or 0)
    expense_record = {
        "so_tien": amount,
        "ngay": today.isoformat(),
        "ngay_mac_dinh": False,
        "noi_dung": f"Thanh toán {bill.get('name') or bill.get('ten')} ({cycle_month})",
        "danh_muc": "Hóa đơn tiện ích",
        "cua_hang": None,
        "truong_thieu": [],
        "canh_bao": [],
        "trang_thai": "da_xac_nhan",
    }
    return True, expense_record, f"Đã xác nhận thanh toán '{bill.get('name')}' ({amount:,}đ).".replace(",", ".")


if __name__ == "__main__":
    print("Smart Money Bot - thử tạo lịch & nhắc hạn hóa đơn (gõ 'q' để thoát)")
    while True:
        line = input("\nNhập câu tạo lịch (vd: Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026): ").strip()
        if line.lower() in {"q", "quit", "exit"}:
            break
        draft = tach_lich_hoa_don(line)
        print(f"  Tên hóa đơn  : {draft['name'] or '(thiếu)'}")
        print(f"  Số tiền      : {draft['amount_vnd'] or '(thiếu)'}")
        print(f"  Ngày lặp     : {draft['due_day'] or '(thiếu)'}")
        print(f"  Tháng bắt đầu: {draft['start_month']}")
        print(f"  Trạng thái   : {draft['trang_thai']} (Thiếu: {draft['truong_thieu']})")
        if not draft["truong_thieu"]:
            nhac = kiem_tra_nhac_han(draft)
            print(f"  Kiểm tra hạn : {nhac['trang_thai_hien_thi']} (Hạn: {nhac['due_date']})")
            if nhac["thong_bao"]:
                print(f"  Thông báo    : {nhac['thong_bao']}")