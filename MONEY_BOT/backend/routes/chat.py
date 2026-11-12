"""Chat và hóa đơn ảnh/PDF trong Chat. Mọi thao tác ghi đi qua store.once + Idempotency-Key."""
import hashlib
from typing import Literal

from fastapi import Depends, File, UploadFile
from pydantic import Field, StrictInt

from finance import FinanceStore
from money_chat.models import Draft
from ocr import extract_text, receipt_fields
from routes import StrictModel


class ChatAction(StrictModel):
    action: Literal["send", "edit", "confirm", "cancel", "retry"]
    text: str = Field(default="", max_length=2000)
    draft_id: str = ""
    version: StrictInt = 0
    fields: dict = Field(default_factory=dict)


def register(app, d):
    @app.get("/api/chat/{sid}")
    def chat_get(sid: str, s: FinanceStore = Depends(d.store)):
        with s.atomic():
            return d.snapshot(d.engine(s, sid))

    @app.post("/api/chat/{sid}")
    def chat_action(sid: str, body: ChatAction, k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        e = d.engine(s, sid)
        def apply():
            if body.action == "send":
                message = e.send(body.text)
            elif body.action == "edit":
                message = e.edit(body.draft_id, body.version, body.fields)
            elif body.action == "confirm":
                message = e.confirm(body.draft_id, body.version)
            elif body.action == "cancel":
                current = e.snapshot()["draft"]
                if not current or (current["id"], current["version"]) != (body.draft_id, body.version):
                    raise ValueError("Bản nháp đã thay đổi; tải lại trước khi hủy.")
                message = e.send("hủy")
            else:
                message = e.retry()
            return d.snapshot(e, message)
        return s.once(k, [sid, body.model_dump()], apply)

    @app.post("/api/chat/{sid}/receipt")
    def receipt(sid: str, file: UploadFile = File(...), k: str = Depends(d.key), s: FinanceStore = Depends(d.store)):
        e = d.engine(s, sid)
        content = file.file.read(10 * 1024 * 1024 + 1)
        if len(content) > 10 * 1024 * 1024:
            raise ValueError("Tệp tối đa 10 MB.")
        digest = hashlib.sha256(content).hexdigest()
        cached = s.cached(k, [sid, "receipt", digest])
        if cached is not None:
            return cached
        # OCR chạy ngoài transaction khóa ghi; tạo nháp và cache phản hồi nguyên tử sau đó.
        text = extract_text(content)
        fields, issues = receipt_fields(text, d.today())
        def apply():
            state = e.store.load_session(sid)
            if state["draft"] or state["pending"]:
                raise ValueError("Hãy hoàn tất hoặc hủy bản nháp hiện tại trước khi gửi hóa đơn.")
            draft = Draft(intent="record_expense", fields=fields, issues=issues).to_dict()
            state["draft"] = draft
            state["history"].append({"role": "user", "content": "[Ảnh/PDF hóa đơn]"})
            message = e.evaluate(state)
            return {**d.snapshot(e, message), "ocr_text": text[:12000], "ocr_note": "OCR có thể sai, đặc biệt dấu tiếng Việt. Kiểm tra tiền/ngày/danh mục trước xác nhận."}
        return s.once(k, [sid, "receipt", digest], apply)
