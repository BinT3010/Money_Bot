"""OCR thực chạy cục bộ bằng ONNX; PDF có text dùng text layer, bản scan dùng OCR.

Không xem confidence là độ chính xác đã đo. Kết quả chỉ tạo bản nháp cần người dùng sửa/xác nhận.
"""
import io
import re
import threading
from contextlib import closing
from datetime import date
from PIL import Image, ImageOps
from moneybot.money_chat.parser import fold, RuleInterpreter
from moneybot.classification import TeamClassifier

_lock = threading.Lock()
_engine = None
Image.MAX_IMAGE_PIXELS = 20_000_000


def image_text(image):
    global _engine
    import numpy as np
    from rapidocr_onnxruntime import RapidOCR
    image = ImageOps.exif_transpose(image).convert("RGB")
    if image.width * image.height > 20_000_000:
        raise ValueError("Ảnh quá lớn; tối đa 20 megapixel.")
    image.thumbnail((2400, 2400))
    with _lock:
        if _engine is None:
            _engine = RapidOCR(intra_op_num_threads=2, inter_op_num_threads=2)
        rows, _ = _engine(np.asarray(image))
    if not rows:
        return ""
    # OCR thường trả nhãn TOTAL và số tiền thành hai box riêng cùng một dòng.
    lines = []
    for box, text, score in sorted(rows, key=lambda r: sum(p[1] for p in r[0]) / 4):
        cy = sum(p[1] for p in box) / 4
        height = max(p[1] for p in box) - min(p[1] for p in box)
        if lines and abs(lines[-1][0] - cy) < max(8, height * 0.5):
            lines[-1][1].append((min(p[0] for p in box), text))
        else:
            lines.append((cy, [(min(p[0] for p in box), text)]))
    return "\n".join(" ".join(t for _, t in sorted(parts)) for _, parts in lines)


def extract_text(content):
    if not content or len(content) > 10 * 1024 * 1024:
        raise ValueError("Tệp phải từ 1 byte đến 10 MB.")
    if content.startswith(b"%PDF"):
        import pypdfium2 as pdfium
        texts = []
        try:
            with pdfium.PdfDocument(content) as doc:
                if not 1 <= len(doc) <= 5:
                    raise ValueError("PDF tối đa 5 trang.")
                for page in doc:
                    with closing(page):
                        with closing(page.get_textpage()) as tp:
                            text = tp.get_text_range().strip()
                        if len(text) < 20:
                            if page.get_width() * page.get_height() * 4 > 20_000_000:
                                raise ValueError("Kích thước trang PDF quá lớn.")
                            bitmap = page.render(scale=2)
                            try:
                                text = image_text(bitmap.to_pil())
                            finally:
                                bitmap.close()
                        texts.append(text)
        except ValueError:
            raise
        except Exception as exc:
            raise ValueError("Không đọc được PDF; kiểm tra tệp hoặc bỏ mật khẩu.") from exc
        return "\n".join(texts)
    try:
        with Image.open(io.BytesIO(content)) as image:
            if image.format not in {"JPEG", "PNG", "WEBP"}:
                raise ValueError("Chỉ nhận PNG, JPEG, WEBP hoặc PDF.")
            return image_text(image)
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError("Không đọc được ảnh hóa đơn.") from exc


def receipt_fields(text, today):
    """Chỉ chọn tiền trên dòng tổng; nhiều giá trị khác nhau thì để người dùng nhập.
    Không lấy số lớn nhất vì đó có thể là tiền khách đưa hoặc số điện thoại.
    """
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    amounts = set()
    for line in lines:
        normalized = fold(line)
        if re.search(r"\b(tong cong|tong tien|thanh tien|thanh toan|total|amount due)(?=\W|\d|$)", normalized) and not re.search(r"sub\s*total|tam tinh|tien khach|tien thua", normalized):
            for match in re.finditer(r"\d[\d., ]*\d|\d", line):
                raw = match.group().strip().replace(" ", "")
                # VND có thể in 45,000.00 hoặc 45.000,00. Không nhân tiền lên 100 lần.
                if re.search(r"[.,]\d{2}$", raw):
                    if raw[-2:] != "00":
                        continue  # Không tự diễn giải số tiền có phần lẻ.
                    raw = raw[:-3]
                number = re.sub(r"\D", "", raw)
                if number and 0 < int(number) <= 10**12:
                    amounts.add(int(number))
    fields = {"description": lines[0][:300] if lines else "", "date": today.isoformat(), "date_source": "default_today"}
    issues = {}
    if len(amounts) == 1:
        fields["amount_vnd"] = amounts.pop()
    else:
        issues["amount_vnd"] = "Không đọc chắc chắn tổng tiền; hãy nhập số tiền trên hóa đơn."
    if re.search(r"\b(USD|EUR|GBP|JPY|CNY|THB)\b|[$€£¥]", text, re.I):
        fields.pop("amount_vnd", None)
        issues["amount_vnd"] = "Phát hiện ngoại tệ; ứng dụng chỉ lưu VND. Hãy nhập số tiền VND thực đã chi, không tự chuyển đổi."
    dates = re.findall(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b", text)
    unique = set()
    for d, m, y in dates:
        try:
            unique.add(date(int(y), int(m), int(d)).isoformat())
        except ValueError:
            issues["date"] = "Ngày trên hóa đơn không hợp lệ; hãy kiểm tra."
    if len(unique) == 1:
        fields.update(date=unique.pop(), date_source="ocr")
    elif len(unique) > 1:
        issues["date"] = "Hóa đơn có nhiều ngày; hãy chọn ngày chi."
    # Phân loại toàn văn hóa đơn bằng đúng bộ phân loại dùng trong Chat.
    category = TeamClassifier().classify(text)
    if category:
        fields.update(category_id=category, category_source="suggested")
    return fields, issues
