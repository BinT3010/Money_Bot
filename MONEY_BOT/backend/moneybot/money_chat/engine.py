"""E01–E09: máy trạng thái hội thoại — phần chính của Đức Duy."""
import re

from .models import Draft, CATEGORIES, WRITE_INTENTS, INTENTS, validate, money, local_today
from .parser import RuleInterpreter, detect_intent, fold
from .services import KeywordClassifier

HELP = "Mình hỗ trợ: ghi khoản chi, xem tổng theo tháng, tạo hóa đơn hằng tháng và xem khoản cần trả. Ví dụ: Ăn trưa 45k."


class ChatEngine:
    # E01: tiêm phụ thuộc để đổi classifier/backend/AI mà không sửa luồng Chat.
    def __init__(self, store, session_id="cli", today=None, interpreter=None, classifier=None, backend=None):
        self.store, self.session_id = store, session_id
        self.today = today or local_today()
        self.interpreter = interpreter or RuleInterpreter()
        self.classifier = classifier or KeywordClassifier()
        self.backend = backend or store

    def snapshot(self):
        body = self.store.load_session(self.session_id)
        draft = body["draft"]
        body["errors"] = self.errors(draft) if draft else {}
        body["can_confirm"] = bool(draft and draft["intent"] in WRITE_INTENTS and not body["errors"] and not body["pending"])
        return body

    def errors(self, draft):
        return validate(draft["intent"], draft["fields"], self.today) | draft["issues"]

    def reply(self, body, message):
        body["history"].append({"role": "assistant", "content": message})
        self.store.save_session(self.session_id, body)
        return message

    # E02: điều hướng lệnh. Chỉ câu xác nhận chính xác mới gọi confirm().
    def send(self, text):
        text = text.strip()
        if not text or len(text) > 2000:
            return "Nhập từ 1 đến 2.000 ký tự."
        body = self.store.load_session(self.session_id)
        body["history"].append({"role": "user", "content": text})
        self.store.save_session(self.session_id, body)
        t = fold(text).strip(" .!")
        if body["pending"]:
            if t in {"thu lai", "kiem tra ket qua"}:
                return self.retry()
            return self.reply(body, "Đang kiểm tra kết quả lưu. Chọn Kiểm tra kết quả; chưa thể sửa hoặc hủy thao tác này.")
        if t == "xac nhan luu":
            draft = body["draft"]
            return self.confirm(draft["id"], draft["version"]) if draft else self.reply(body, "Hiện không có bản nháp để lưu.")
        if t in {"huy", "huy ban nhap"}:
            body.update(draft=None, state="idle", switch_text=None)
            return self.reply(body, "Đã hủy bản nháp, chưa ghi khoản chi hoặc lịch hóa đơn.")
        if body["switch_text"]:
            if t == "huy va chuyen":
                new_text = body["switch_text"]
                body.update(draft=None, switch_text=None, state="idle")
                self.store.save_session(self.session_id, body)
                return self.send(new_text)
            if t == "giu ban nhap":
                body["switch_text"] = None
                return self.reply(body, "Đã giữ bản nháp. Bạn có thể bổ sung, sửa hoặc xác nhận.")
            return self.reply(body, "Chọn 'hủy và chuyển' hoặc 'giữ bản nháp'.")
        kind = detect_intent(text)
        if kind == "payment_unsupported":
            return self.reply(body, "Để cập nhật đã trả, mở tab Hóa đơn, chọn đúng kỳ và xác nhận thanh toán hoặc liên kết khoản chi đã có. Chat không tự thanh toán từ câu nhập.")
        if kind == "unsupported":
            return self.reply(body, HELP)
        draft = body["draft"]
        # MONEY có thể làm nhận nhầm câu bổ sung cho hóa đơn thành khoản chi, nên khoản chi
        # chỉ được coi là yêu cầu mới khi nháp đang là câu hỏi (không có trường tiền để bổ sung).
        switching = kind in {"query_spending", "list_due_bills", "create_recurring_bill"} or (
            kind == "record_expense" and draft and draft["intent"] in {"query_spending", "list_due_bills"})
        if draft and switching and kind != draft["intent"]:
            body["switch_text"] = text
            return self.reply(body, "Bạn đang có bản nháp. Gõ 'hủy và chuyển' để đổi yêu cầu, hoặc 'giữ bản nháp'.")
        if t == "thu lai" and draft:
            return self.evaluate(body)
        if t in {"u", "ok", "duoc roi", "vang"}:
            return self.reply(body, "Nếu muốn lưu, dùng nút Xác nhận hoặc gõ 'xác nhận lưu'.")
        try:
            parsed = self.interpreter.parse(text, self.today,
                                            draft["intent"] if draft else None,
                                            draft["fields"] if draft else {})
        except Exception:
            # Không lộ nội dung lỗi HTTP/khóa API; vẫn còn đường nhập bằng mẫu.
            return self.reply(body, "Chưa hiểu được câu này do dịch vụ lỗi. Bản nháp vẫn giữ nguyên; thử lại hoặc nhập bằng biểu mẫu.")
        if parsed.multiple:
            return self.reply(body, "Mỗi lần chỉ ghi một khoản. Hãy gửi lại riêng từng khoản, ví dụ: Ăn trưa 45k.")
        if not draft and parsed.intent not in INTENTS:
            return self.reply(body, HELP)
        # Câu có cả nội dung và số tiền khi đã có nháp khoản chi là khoản chi mới, không phải câu bổ sung:
        # giữ nháp cũ để người dùng còn sửa/xác nhận được. Câu "sửa/đổi ..." và câu chỉ có tiền/ngày vẫn sửa nháp.
        if (draft and draft["intent"] == "record_expense" and parsed.intent == "record_expense"
                and draft["fields"].get("description") and parsed.fields.get("description")
                and parsed.fields.get("amount_vnd") is not None and not re.match(r"(sua|doi)\b", t)):
            body["switch_text"] = text
            return self.reply(body, f"Bạn đang có bản nháp \"{draft['fields']['description']}\" chưa lưu. "
                                    "Gõ 'hủy và chuyển' để bỏ nháp đó và tạo khoản mới, hoặc 'giữ bản nháp' để tiếp tục sửa/xác nhận.")
        if not draft:
            draft = Draft(intent=parsed.intent).to_dict()
            if parsed.intent == "record_expense":
                draft["fields"].update(date=self.today.isoformat(), date_source="default_today")
            body["draft"] = draft
        return self.merge(body, parsed.fields, parsed.issues)

    # E03: mọi sửa đổi tăng version; lỗi cũ chỉ xóa khi trường đó được sửa.
    def merge(self, body, changes, issues=None):
        draft = body["draft"]
        old_description = draft["fields"].get("description")
        for key in changes:
            draft["issues"].pop(key, None)
        draft["fields"].update(changes)
        draft["issues"].update(issues or {})
        draft["version"] += 1
        fields = draft["fields"]
        if draft["intent"] == "record_expense" and fields.get("category_source") != "user":
            if old_description != fields.get("description") or "category_id" not in fields:
                fields.pop("category_id", None)
                if fields.get("description"):
                    try:
                        category = self.classifier.classify(fields["description"])
                        if category in CATEGORIES:
                            fields.update(category_id=category, category_source="suggested")
                    except Exception:
                        pass  # không đoán nhãn khi dịch vụ phân loại lỗi
        return self.evaluate(body)

    # E04: điểm nối cho form của Nguyên; kiểm tra phiên bản trước khi sửa.
    def edit(self, draft_id, version, changes):
        body = self.store.load_session(self.session_id)
        draft = body["draft"]
        if body["pending"] or not draft or (draft["id"], draft["version"]) != (draft_id, version):
            return self.reply(body, "Bản nháp đã thay đổi hoặc đang lưu. Hãy xem phiên bản mới nhất.")
        body["switch_text"] = None  # sửa bằng biểu mẫu nghĩa là giữ bản nháp hiện tại
        allowed = {"description", "name", "date", "amount_vnd", "category_id", "due_day", "start_month", "month"}
        changes = {k: v for k, v in changes.items() if k in allowed}
        if "category_id" in changes:
            changes["category_source"] = "user"
        if "date" in changes:
            changes["date_source"] = "user"
        return self.merge(body, changes)

    # E05: đọc không ghi. Lỗi đọc giữ yêu cầu để thử lại, không trả số 0 giả.
    def evaluate(self, body):
        draft = body["draft"]
        errors = self.errors(draft)
        if errors:
            body["state"] = "collecting"
            return self.reply(body, "Cần bổ sung/kiểm tra: " + " ".join(errors.values()))
        if draft["intent"] in WRITE_INTENTS:
            body["state"] = "awaiting_confirmation"
            f = draft["fields"]
            if draft["intent"] == "record_expense":
                detail = f"{f['description']} · {money(f['amount_vnd'])} · {CATEGORIES[f['category_id']]} · {f['date']}"
                if f.get("date_source") == "default_today":
                    detail += " (ngày hôm nay được đề xuất)"
            else:
                detail = f"{f['name']} · {money(f['amount_vnd'])} · ngày {f['due_day']} mỗi tháng · từ {f['start_month']}. Tạo lịch chưa ghi khoản chi"
            return self.reply(body, "Bản nháp: " + detail + ". Bạn có thể sửa, hủy hoặc xác nhận lưu.")
        try:
            if draft["intent"] == "query_spending":
                result = self.backend.summary(draft["fields"]["month"], draft["fields"].get("category_id"))
                message = f"Tháng {result['month']}: đã ghi {money(result['total'])} qua {result['count']} khoản chi."
                if result["count"] == 0:
                    message += " Chưa có khoản chi được ghi trong kỳ này."
            else:
                result = self.backend.due_bills(self.today)
                lines = [f"Hóa đơn từ {result['start']} đến {result['end']}, kèm các kỳ quá hạn chưa trả:"]
                lines += [f"• {r['name']}: {money(r['amount_vnd'])}, hạn {r['due_date']}, {r['status']} ({r['days']} ngày so với hôm nay)." for r in result["items"]]
                if not result["items"]:
                    lines.append("Không có kỳ chưa trả trong phạm vi này.")
                message = "\n".join(lines)
        except Exception:
            body["state"] = "recoverable_error"
            return self.reply(body, "Chưa lấy được dữ liệu. Gõ 'thử lại'; không thể kết luận tổng bằng 0 hoặc không có hóa đơn.")
        body.update(draft=None, state="idle")
        return self.reply(body, message)

    # E06: lưu pending trước khi gọi backend để có thể khôi phục sau crash.
    def confirm(self, draft_id, version):
        body = self.store.load_session(self.session_id)
        if body["pending"]:
            return self.retry()
        draft = body["draft"]
        if not draft or (draft["id"], draft["version"]) != (draft_id, version):
            return self.reply(body, "Bản nháp đã thay đổi hoặc đã xử lý. Không ghi lại.")
        if draft["intent"] not in WRITE_INTENTS or self.errors(draft):
            return self.evaluate(body)
        body["pending"] = {"key": f"{draft_id}:{version}", "intent": draft["intent"], "fields": dict(draft["fields"])}
        body["state"] = "submitting"
        self.store.save_session(self.session_id, body)
        return self.submit(body)

    # E07: lỗi không rõ kết quả không được hiểu là chắc chắn chưa lưu.
    def submit(self, body):
        command = body["pending"]
        try:
            receipt = self.backend.commit(command["key"], command["intent"], command["fields"], self.today)
        except ValueError as exc:
            body.update(pending=None, state="collecting")
            return self.reply(body, "Yêu cầu bị từ chối: " + str(exc))
        except Exception:
            body["state"] = "checking_result"
            return self.reply(body, "Chưa rõ kết quả lưu. Chọn Kiểm tra kết quả; không cần nhập lại khoản chi.")
        return self.complete(body, receipt)

    # E08: retry cùng key/cùng payload, backend bảo đảm idempotency.
    def retry(self):
        body = self.store.load_session(self.session_id)
        if not body["pending"]:
            return self.evaluate(body) if body["draft"] else self.reply(body, "Không có yêu cầu đang chờ.")
        try:
            receipt = self.backend.operation_status(body["pending"]["key"])
        except Exception:
            return self.reply(body, "Chưa kiểm tra được kết quả. Giữ nguyên yêu cầu để kiểm tra lại sau.")
        return self.complete(body, receipt) if receipt else self.submit(body)

    # E09: chỉ biên nhận thành công mới được nói 'đã lưu'.
    def complete(self, body, receipt):
        body.update(draft=None, pending=None, state="idle", last_receipt=receipt, switch_text=None)
        noun = "khoản chi" if receipt["intent"] == "record_expense" else "lịch hóa đơn (chưa ghi chi)"
        return self.reply(body, f"Đã lưu {noun}: {money(receipt['data']['amount_vnd'])}. Mã: {receipt['id']}.")
