"""
classifier.py - Phân loại khoản chi từ câu tiếng Việt (Smart Money Bot)

Phạm vi: nhận một câu ngắn như "ăn trưa 45k" và đề xuất
  số tiền, ngày, nội dung, danh mục để NGƯỜI DÙNG KIỂM TRA VÀ XÁC NHẬN.
Module này KHÔNG lưu dữ liệu và KHÔNG gọi dịch vụ ngoài.

Quy tắc theo báo cáo nhóm:
  - Mỗi giao dịch chỉ có MỘT danh mục chính (một trong 6 danh mục).
  - Không đủ căn cứ (không khớp từ khóa, hoặc hòa điểm) -> danh_muc = None,
    người dùng tự chọn. Không đoán bừa.
  - Không phân loại chỉ từ tên cửa hàng (module không dùng tên cửa hàng làm từ khóa).
  - Trường bắt buộc: ngày, số tiền dương, nội dung, danh mục.
  - Câu không nêu ngày -> đề xuất ngày hiện tại (ngay_mac_dinh = True).
  - Chỉ lưu sau khi xác nhận (việc lưu do phần khác đảm nhiệm).

Cách dùng:
    from classifier import classify
    ket_qua = classify("ăn trưa 45k")

Chạy thử trong terminal:
    python classifier.py
"""

import re
import unicodedata
from datetime import date, timedelta

# --------------------------------------------------------------------------
# 1. Danh mục chuẩn (dùng đúng tên trong báo cáo nhóm)
# --------------------------------------------------------------------------
CATEGORIES = [
    "Ăn uống",
    "Tiêu dùng/Mua sắm",
    "Đi lại",
    "Hóa đơn tiện ích",
    "Giải trí",
    "Khác",
]

# Từ khóa cho từng danh mục. Có thể thêm/bớt thoải mái, không cần sửa code bên dưới.
# Cụm từ dài được ưu tiên hơn cụm ngắn (vd "tiền nước" thắng "nước").
KEYWORDS = {
    "Ăn uống": [
        "ăn", "ăn sáng", "ăn trưa", "ăn tối", "ăn vặt", "ăn khuya", "bữa", "cơm",
        "phở", "bún", "bánh mì", "hủ tiếu", "cháo", "xôi", "mì", "lẩu", "nướng",
        "pizza", "gà rán", "burger", "cà phê", "cafe", "coffee", "trà", "trà sữa",
        "trà đá", "nước ngọt", "nước mía", "sinh tố", "kem", "bia", "nhậu", "uống",
        "đồ uống", "đồ ăn", "đặt đồ ăn", "grabfood", "shopeefood", "quán ăn",
        "tiệm bánh", "bánh", "snack",
    ],
    "Tiêu dùng/Mua sắm": [
        "xe đồ chơi", "đồ chơi", "bàn học",
        "quần áo", "áo", "quần", "váy", "giày", "dép", "mỹ phẩm", "son", "kem chống nắng",
        "sữa rửa mặt", "dầu gội", "sữa tắm", "bột giặt", "nước rửa chén", "giấy vệ sinh",
        "siêu thị", "tạp hóa", "nhu yếu phẩm", "đồ gia dụng", "tai nghe", "ốp lưng",
        "sạc", "balo", "túi xách", "shopee", "lazada", "tiki", "mua sắm", "shopping",
    ],
    "Đi lại": [
        "xăng", "đổ xăng", "grab", "gojek", "xe ôm", "taxi", "vé xe", "xe buýt",
        "xe bus", "buýt", "bus", "gửi xe", "giữ xe", "vé tàu", "vé máy bay", "máy bay",
        "rửa xe", "bảo dưỡng xe", "thay nhớt", "nhớt", "đặt xe", "grabcar",
        "grabbike", "metro", "phí cầu đường", "đi lại", "di chuyển",
    ],
    "Hóa đơn tiện ích": [
        "tiền điện", "điện", "tiền nước", "nước sinh hoạt", "internet", "wifi",
        "mạng", "cước", "cước điện thoại", "tiền phòng", "tiền nhà", "thuê phòng",
        "thuê nhà", "tiền trọ", "nạp tiền điện thoại", "nạp thẻ", "data", "4g",
        "gas", "tiền gas", "truyền hình cáp", "hóa đơn", "phí chung cư",
    ],
    "Giải trí": [
        "xem phim", "vé phim", "phim", "rạp", "netflix", "spotify", "youtube premium",
        "game", "nạp game", "karaoke", "concert", "sự kiện", "vé sự kiện",
        "sách truyện", "truyện", "bowling", "bida", "billiard", "đi chơi",
        "thuê bao giải trí", "giải trí",
    ],
    "Khác": [
        "sách học",
        "thuốc", "khám bệnh", "bệnh viện", "nhà thuốc", "vitamin", "khẩu trang",
        "y tế", "phòng khám", "vở", "bút", "sách giáo khoa", "photo", "in tài liệu",
        "văn phòng phẩm", "đồ dùng học tập", "học phí",
    ],
}

# Cụm từ được "nuốt" để không khớp nhầm từ ngắn bên trong (vd "điện thoại" != tiền điện).
IGNORE_PHRASES = ["điện thoại"]

_MAX_PHRASE_LEN = 4


# --------------------------------------------------------------------------
# 2. Chuẩn hóa chữ: bỏ dấu nhưng GIỮ NGUYÊN ĐỘ DÀI chuỗi (để cắt nội dung chính xác)
# --------------------------------------------------------------------------
def _fold(text):
    """Bỏ dấu tiếng Việt, đ->d, chữ thường. Độ dài chuỗi không đổi (sau khi NFC)."""
    out = []
    for ch in text:
        if ch in "đĐ":
            out.append("d")
            continue
        base = "".join(c for c in unicodedata.normalize("NFD", ch) if not unicodedata.combining(c))
        out.append((base or ch).lower())
    return "".join(out)


def _tokens(folded):
    return re.findall(r"[a-z0-9]+", folded)


def _build_lookup():
    lookup = {}
    for category, words in KEYWORDS.items():
        for w in words:
            lookup[tuple(_tokens(_fold(w)))] = category
    ignore = {tuple(_tokens(_fold(w))) for w in IGNORE_PHRASES}
    return lookup, ignore


_LOOKUP, _IGNORE = _build_lookup()


# --------------------------------------------------------------------------
# 3. Tách ngày
# --------------------------------------------------------------------------
_RE_DATE = re.compile(
    r"(?:ngay\s*)?(?<![\d.,])(?P<d>\d{1,2})[/-](?P<m>\d{1,2})(?:[/-](?P<y>\d{4}|\d{2}))?(?![\d])"
)
_RE_REL_DATE = re.compile(r"(?<![a-z])(?P<w>hom nay|hom qua|hom kia|bua qua|sang nay|trua nay|chieu nay|toi nay)(?![a-z])")
_REL_OFFSET = {"hom qua": -1, "bua qua": -1, "hom kia": -2}


def _extract_date(folded, today):
    """Trả về (iso_date, ngay_mac_dinh, span hoặc None, cảnh báo hoặc None)."""
    m = _RE_DATE.search(folded)
    if m:
        d, mo = int(m.group("d")), int(m.group("m"))
        y = m.group("y")
        year = today.year if not y else (int(y) + 2000 if len(y) == 2 else int(y))
        try:
            return date(year, mo, d).isoformat(), False, m.span(), None
        except ValueError:
            return today.isoformat(), True, m.span(), f"Ngày '{m.group(0).strip()}' không hợp lệ, tạm dùng ngày hôm nay."
    m = _RE_REL_DATE.search(folded)
    if m:
        offset = _REL_OFFSET.get(m.group("w"), 0)
        return (today + timedelta(days=offset)).isoformat(), False, m.span(), None
    return today.isoformat(), True, None, None


# --------------------------------------------------------------------------
# 4. Tách số tiền
# --------------------------------------------------------------------------
_P_TRIEU = re.compile(
    r"(?P<n>\d+(?:[.,]\d+)?)\s*(?:trieu|tr|cu)(?![a-z])"
    r"(?:(?P<rest>\d{1,3})(?![\d.,a-z])|\s*(?P<half>ruoi)(?![a-z]))?"
)
_P_K = re.compile(r"(?P<n>\d+(?:[.,]\d+)?)\s*(?:k|nghin|ngan)(?![a-z])")
_P_SEP = re.compile(r"(?<![\d.,])(?P<n>\d{1,3}(?:[.,]\d{3})+)(?!\d)\s*(?:d|dong|vnd)?(?![a-z])")
_P_UNIT = re.compile(r"(?<![\d.,])(?P<n>\d+)\s*(?:d|dong|vnd)(?![a-z])")
_P_BARE = re.compile(r"(?<![\d.,/])(?P<n>\d+)(?![\d.,/]|[a-z])")


def _num(s):
    return float(s.replace(",", "."))


def _value_trieu(m):
    value = _num(m.group("n")) * 1_000_000
    rest, half = m.group("rest"), m.group("half")
    if rest:  # "1tr5" = 1,5 triệu; "1tr25" = 1,25 triệu; "1tr250" = 1,25 triệu
        value += int(rest) * {1: 100_000, 2: 10_000, 3: 1_000}[len(rest)]
    elif half:  # "2 triệu rưỡi"
        value += 500_000
    return round(value)


def _extract_amounts(folded, blocked_spans):
    """Trả về (danh sách (start, end, value), cảnh báo)."""
    found = []
    warnings = []

    def free(span):
        for a, b in blocked_spans + [(s, e) for s, e, _ in found]:
            if span[0] < b and a < span[1]:
                return False
        return True

    for pattern, to_value in (
        (_P_TRIEU, _value_trieu),
        (_P_K, lambda m: round(_num(m.group("n")) * 1000)),
        (_P_SEP, lambda m: int(re.sub(r"[.,]", "", m.group("n")))),
        (_P_UNIT, lambda m: int(m.group("n"))),
    ):
        for m in pattern.finditer(folded):
            if free(m.span()):
                found.append((m.start(), m.end(), to_value(m)))

    # Số trần: >= 1000 coi là số tiền; < 1000 là mơ hồ (45 = 45k hay 45đ?)
    ambiguous = []
    for m in _P_BARE.finditer(folded):
        if not free(m.span()):
            continue
        n = int(m.group("n"))
        if n >= 1000:
            found.append((m.start(), m.end(), n))
        elif n > 0:
            ambiguous.append(m)

    found.sort()
    if not found and ambiguous:
        first = ambiguous[0].group("n")
        warnings.append(f"Số '{first}' chưa rõ đơn vị. Bạn nhập {first}k hay {first}.000đ?")
    return found, warnings


# --------------------------------------------------------------------------
# 5. Phân loại bằng từ khóa
# --------------------------------------------------------------------------
def _match_keywords(tokens):
    """Khớp cụm dài nhất trước. Trả về điểm theo danh mục và danh sách từ khóa khớp."""
    scores = {c: 0 for c in CATEGORIES}
    hits = []
    i = 0
    while i < len(tokens):
        for length in range(min(_MAX_PHRASE_LEN, len(tokens) - i), 0, -1):
            phrase = tuple(tokens[i:i + length])
            if phrase in _IGNORE:
                i += length
                break
            category = _LOOKUP.get(phrase)
            if category:
                scores[category] += length  # cụm càng dài càng đáng tin
                hits.append((category, " ".join(phrase)))
                i += length
                break
        else:
            i += 1
    return scores, hits


def _pick_category(scores):
    top = max(scores.values())
    if top == 0:
        return None, []
    best = [c for c, s in scores.items() if s == top]
    return (best[0], []) if len(best) == 1 else (None, best)


# --------------------------------------------------------------------------
# 6. Hàm chính
# --------------------------------------------------------------------------
def classify(text, today=None):
    """
    Đề xuất bản ghi giao dịch từ một câu tiếng Việt.

    Trả về dict:
      so_tien        int hoặc None
      ngay           "YYYY-MM-DD"
      ngay_mac_dinh  True nếu câu không nêu ngày (đang dùng ngày hôm nay)
      noi_dung       str (có thể rỗng)
      danh_muc       một trong CATEGORIES hoặc None (người dùng phải chọn)
      cua_hang       luôn None ở phiên bản này
      truong_thieu   list trường bắt buộc còn thiếu
      canh_bao       list thông báo hiển thị cho người dùng
      tu_khoa_khop   list [(danh mục, từ khóa)] làm căn cứ phân loại
      ung_vien       list danh mục hòa điểm (để gợi ý nút chọn)
      trang_thai     "can_bo_sung" hoặc "cho_xac_nhan"
    """
    today = today or date.today()
    original = unicodedata.normalize("NFC", (text or "").strip())
    folded = _fold(original)

    iso_date, is_default, date_span, date_warning = _extract_date(folded, today)
    blocked = [date_span] if date_span else []
    amounts, warnings = _extract_amounts(folded, blocked)
    if date_warning:
        warnings.append(date_warning)

    amount = amounts[0][2] if amounts else None
    if len({v for _, _, v in amounts}) > 1:
        warnings.append(
            f"Câu có nhiều số tiền khác nhau, đang lấy số đầu tiên ({amount:,}đ). Hãy kiểm tra lại.".replace(",", ".")
        )

    # Che phần ngày + tiền rồi mới phân loại và lấy nội dung
    spans = blocked + [(s, e) for s, e, _ in amounts]
    masked_folded = list(folded)
    masked_original = list(original)
    for s, e in spans:
        for k in range(s, e):
            masked_folded[k] = " "
            masked_original[k] = " "
    masked_folded = "".join(masked_folded)
    content = re.sub(r"\s+", " ", "".join(masked_original)).strip(" -,.:;")

    scores, hits = _match_keywords(_tokens(masked_folded))
    category, candidates = _pick_category(scores)
    if category is None and candidates:
        warnings.append("Khớp nhiều danh mục: " + ", ".join(candidates) + ". Vui lòng chọn một.")

    missing = []
    if amount is None:
        missing.append("so_tien")
    if not content:
        missing.append("noi_dung")
    if category is None:
        missing.append("danh_muc")

    return {
        "so_tien": amount,
        "ngay": iso_date,
        "ngay_mac_dinh": is_default,
        "noi_dung": content,
        "danh_muc": category,
        "cua_hang": None,
        "truong_thieu": missing,
        "canh_bao": warnings,
        "tu_khoa_khop": hits,
        "ung_vien": candidates,
        "trang_thai": "can_bo_sung" if missing else "cho_xac_nhan",
    }


def kiem_tra_ban_ghi(record):
    """
    Kiểm tra lại bản ghi sau khi người dùng sửa/bổ sung (vd chọn danh mục từ dropdown).
    Cập nhật truong_thieu và trang_thai. Trả về chính record đó.
    """
    missing = []
    amount = record.get("so_tien")
    if not isinstance(amount, int) or isinstance(amount, bool) or amount <= 0:
        missing.append("so_tien")
    if not str(record.get("noi_dung") or "").strip():
        missing.append("noi_dung")
    if record.get("danh_muc") not in CATEGORIES:
        missing.append("danh_muc")
    try:
        date.fromisoformat(str(record.get("ngay")))
    except ValueError:
        missing.append("ngay")
    record["truong_thieu"] = missing
    record["trang_thai"] = "can_bo_sung" if missing else "cho_xac_nhan"
    return record


# --------------------------------------------------------------------------
# 7. Chạy thử trong terminal
# --------------------------------------------------------------------------
if __name__ == "__main__":
    print("Smart Money Bot - thử phân loại (gõ 'q' để thoát)")
    while True:
        line = input("\nBạn: ").strip()
        if line.lower() in {"q", "quit", "exit"}:
            break
        r = classify(line)
        so_tien = f"{r['so_tien']:,}đ".replace(",", ".") if r["so_tien"] else "(thiếu)"
        print(f"  Số tiền : {so_tien}")
        print(f"  Ngày    : {r['ngay']}" + (" (mặc định hôm nay)" if r["ngay_mac_dinh"] else ""))
        print(f"  Nội dung: {r['noi_dung'] or '(thiếu)'}")
        print(f"  Danh mục: {r['danh_muc'] or '(cần chọn)'}")
        for w in r["canh_bao"]:
            print(f"  ! {w}")
        print(f"  Trạng thái: {r['trang_thai']}")
