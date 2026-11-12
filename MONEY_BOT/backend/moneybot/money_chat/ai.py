"""A01–A03: tùy chọn gọi endpoint chat-completions; mặc định không gọi mạng."""
import json
import os
from urllib.request import Request, urlopen
from urllib.parse import urlparse
from .models import Parsed, INTENTS
from .parser import RuleInterpreter, parse_money, parse_date, month_fields


class HybridInterpreter(RuleInterpreter):
    name = "Quy tắc + AI cho câu chưa nhận diện (cần cấu hình API)"

    # A01: không lưu khóa trong source; URL là endpoint đầy đủ do người dùng đặt.
    def __init__(self, endpoint, model, api_key):
        parsed = urlparse(endpoint)
        if parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in {"localhost", "127.0.0.1"}):
            raise ValueError("Endpoint phải HTTPS hoặc HTTP localhost.")
        self.endpoint, self.model, self.api_key = endpoint, model, api_key

    # A02: quy tắc xử lý câu quen thuộc; AI chỉ trích các đoạn có trong đầu vào.
    def parse(self, text, today, intent=None, current=None):
        result = super().parse(text, today, intent, current)
        if result.intent is not None:
            return result
        instruction = (
            "Return ONLY a JSON object with intent and evidence. intent is one of "
            "record_expense, query_spending, create_recurring_bill, list_due_bills, unknown. "
            "evidence contains optional description, amount_text, date_text, month_text, due_day_text. "
            "Every value MUST be an exact substring from the user's message. "
            "Do not invent money, dates or categories. Do not execute or confirm anything. "
            "Vietnamese personal expense assistant. Missing fields must be omitted. "
            "description must exclude money/date phrases. Return no other keys."
        )
        payload = {"model": self.model, "messages": [
            {"role": "system", "content": instruction}, {"role": "user", "content": text}],
            "temperature": 0}
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = "Bearer " + self.api_key
        request = Request(self.endpoint, json.dumps(payload).encode(), headers)
        with urlopen(request, timeout=15) as response:
            raw = response.read(100_001)
        if len(raw) > 100_000:
            raise ValueError("Phản hồi quá lớn.")
        content = json.loads(raw)["choices"][0]["message"]["content"]
        extracted = json.loads(content)
        return self.from_evidence(text, today, extracted)

    # A03: kiểm tra evidence trước chuẩn hóa, không chấp nhận trường AI tự bịa.
    @staticmethod
    def from_evidence(text, today, extracted):
        if not isinstance(extracted, dict) or set(extracted) - {"intent", "evidence"}:
            raise ValueError("Sai cấu trúc AI.")
        kind = extracted.get("intent")
        if kind == "unknown":
            return Parsed()
        if kind not in INTENTS:
            raise ValueError("Ý định không hợp lệ.")
        evidence = extracted.get("evidence", {})
        allowed = {"description", "amount_text", "date_text", "month_text", "due_day_text"}
        if not isinstance(evidence, dict) or set(evidence) - allowed:
            raise ValueError("Sai trường AI.")
        if any(not isinstance(v, str) or not v or v not in text for v in evidence.values()):
            raise ValueError("Thông tin không có trong câu gốc.")
        out = Parsed(intent=kind)
        if "description" in evidence:
            out.fields["name" if kind == "create_recurring_bill" else "description"] = evidence["description"]
        if "amount_text" in evidence:
            value, error, matches = parse_money(evidence["amount_text"])
            if not matches or len(matches) != 1:
                raise ValueError("Chưa chuẩn hóa được số tiền.")
            out.fields["amount_vnd"] = value
            if error:
                out.issues["amount_vnd"] = error
        if "date_text" in evidence:
            value, error = parse_date(evidence["date_text"], today)
            if not value and not error:
                raise ValueError("Chưa chuẩn hóa được ngày.")
            out.fields.update(date=value, date_source="user")
            if error:
                out.issues["date"] = error
        if "month_text" in evidence:
            target = "start_month" if kind == "create_recurring_bill" else "month"
            out.fields.update(month_fields(evidence["month_text"], today, {}, target))
        if "due_day_text" in evidence:
            import re
            match = re.search(r"\b(\d{1,2})\b", evidence["due_day_text"])
            if not match:
                raise ValueError("Ngày lặp chưa rõ.")
            out.fields["due_day"] = int(match[1])
        return out


def configured_interpreter():
    endpoint = os.environ.get("MONEY_CHAT_AI_URL", "").strip()
    model = os.environ.get("MONEY_CHAT_AI_MODEL", "").strip()
    if endpoint and model:
        return HybridInterpreter(endpoint, model, os.environ.get("MONEY_CHAT_AI_KEY", ""))
    return RuleInterpreter()
