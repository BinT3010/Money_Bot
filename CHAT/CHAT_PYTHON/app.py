"""U01–U05: giao diện thử phần Chat, không thay toàn bộ giao diện của Nguyên."""
import os
from datetime import date
from pathlib import Path
from uuid import uuid4
import streamlit as st
from money_chat.engine import ChatEngine
from money_chat.models import CATEGORIES, local_today
from money_chat.storage import SQLiteStore
from money_chat.demo import seed_demo
from money_chat.ai import configured_interpreter

# U01: một URL giữ một session ID; SQLite giữ nháp và pending qua reload.
st.set_page_config(page_title="Smart Money Bot · Đức Duy", page_icon="💬", layout="centered")
st.title("Smart Money Bot")
st.caption("Phần Chat của Đức Duy · bản thử độc lập · không thanh toán thật")
session_id = st.query_params.get("session", "")
if not session_id or len(session_id) > 80:
    session_id = uuid4().hex
    st.query_params["session"] = session_id
data_dir = Path(os.environ.get("MONEY_CHAT_DATA_DIR", str(Path(__file__).parent / "data")))

# U02: dữ liệu thử tách khỏi dữ liệu thường; ngày giả lập được ghi rõ.
with st.sidebar:
    st.header("Chạy thử")
    demo_mode = st.toggle("Dùng bộ dữ liệu demo", value=False)
    use_date = st.toggle("Dùng ngày giả lập", value=demo_mode)
    selected_date = st.date_input("Ngày đánh giá", value=date(2026, 11, 10) if demo_mode else local_today(), disabled=not use_date)
    today = selected_date if use_date else local_today()
    store = SQLiteStore(data_dir / ("demo.db" if demo_mode else "chat.db"))
    if demo_mode and st.button("Thêm dữ liệu mẫu (không thêm trùng)"):
        seed_demo(store)
        st.success("Đã chuẩn bị bộ mẫu; chọn ngày 10/11/2026 để đối chiếu tài liệu.")
    if st.button("Mở phiên Chat mới"):
        st.query_params["session"] = uuid4().hex
        st.rerun()
    st.caption("Ứng dụng dành cho một người dùng trên máy cá nhân. URL có mã phiên; chưa có đăng nhập.")
try:
    interpreter = configured_interpreter()
except ValueError:
    st.error("Cấu hình AI không hợp lệ. Xem hướng dẫn hoặc bỏ biến MONEY_CHAT_AI_URL.")
    st.stop()
engine = ChatEngine(store, session_id, today, interpreter=interpreter)
st.caption(f"Chế độ: {interpreter.name} · Ngày: {today.isoformat()}")
chat_tab, data_tab = st.tabs(["Hội thoại", "Dữ liệu đã lưu"])

with chat_tab:
    body = engine.snapshot()
    if not body["history"]:
        st.info("Thử: Ăn trưa 45k · Tháng này chi bao nhiêu? · Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026")
    # U03: lịch sử chỉ để hiển thị, không dùng để cộng tổng chi.
    for message in body["history"][-60:]:
        with st.chat_message(message["role"]):
            st.write(message["content"])
    if body["pending"]:
        st.warning("Có lệnh ghi chưa xác định kết quả. Không nhập lại khoản này.")
        if st.button("Kiểm tra kết quả"):
            engine.retry()
            st.rerun()
    elif body["switch_text"]:
        left, right = st.columns(2)
        if left.button("Hủy và chuyển"):
            engine.send("hủy và chuyển")
            st.rerun()
        if right.button("Giữ bản nháp"):
            engine.send("giữ bản nháp")
            st.rerun()
    # U04: form chỉnh nháp; thay đổi form không tự ghi nghiệp vụ.
    draft = body["draft"]
    if draft and not body["pending"] and draft["intent"] in {"record_expense", "create_recurring_bill"}:
        fields = draft["fields"]
        st.subheader("Bản nháp — chưa lưu")
        with st.form(f"draft-{draft['id']}-{draft['version']}"):
            name_key = "description" if draft["intent"] == "record_expense" else "name"
            name = st.text_input("Nội dung / tên hóa đơn", value=fields.get(name_key) or "")
            amount = st.number_input("Số tiền VNĐ", min_value=0, max_value=10**12, value=fields.get("amount_vnd") or 0, step=1000)
            changes = {name_key: name, "amount_vnd": amount}
            if name_key == "description":
                day = st.text_input("Ngày (YYYY-MM-DD)", value=fields.get("date") or "")
                options = [""] + list(CATEGORIES)
                selected = fields.get("category_id", "")
                category = st.selectbox("Danh mục", options, index=options.index(selected) if selected in options else 0,
                                        format_func=lambda k: CATEGORIES.get(k, "— Chọn danh mục —"))
                changes.update(date=day)
                # Chỉ ghi nguồn 'user' khi người dùng đổi nhãn; sửa nội dung
                # nhưng giữ nhãn gợi ý cũ thì engine phải phân loại lại.
                if category != fields.get("category_id", ""):
                    changes["category_id"] = category
            else:
                due_day = st.number_input("Ngày lặp", min_value=1, max_value=31, value=min(31, max(1, fields.get("due_day") or 1)))
                month = st.text_input("Tháng bắt đầu (YYYY-MM)", value=fields.get("start_month") or "")
                changes.update(due_day=due_day, start_month=month)
            st.caption("Bấm Cập nhật bản nháp, xem lại rồi dùng nút Xác nhận lưu bên dưới.")
            if st.form_submit_button("Cập nhật bản nháp"):
                engine.edit(draft["id"], draft["version"], changes)
                st.rerun()
        for error in body["errors"].values():
            st.warning(error)
        col1, col2 = st.columns(2)
        if col1.button("Xác nhận lưu", disabled=not body["can_confirm"], type="primary"):
            engine.confirm(draft["id"], draft["version"])
            st.rerun()
        if col2.button("Hủy bản nháp"):
            engine.send("hủy")
            st.rerun()
    col1, col2 = st.columns(2)
    if not draft and not body["pending"]:
        if col1.button("Nhập khoản chi bằng biểu mẫu"):
            engine.send("ghi chi")
            st.rerun()
        if col2.button("Tạo lịch bằng biểu mẫu"):
            engine.send("tạo lịch")
            st.rerun()
    text = st.chat_input("Nhập yêu cầu bằng tiếng Việt…", max_chars=2000, disabled=bool(body["pending"]))
    if text:
        engine.send(text)
        st.rerun()

# U05: màn hình tham chiếu để chứng minh nháp không nằm trong dữ liệu đã lưu.
with data_tab:
    st.subheader("Giao dịch đã lưu")
    st.dataframe(store.expenses(), width="stretch")
    summary = store.summary(today.strftime("%Y-%m"))
    st.metric("Tổng tháng đang đánh giá (VNĐ)", f"{summary['total']:,}")
    st.subheader("Hóa đơn cần trả")
    st.dataframe(store.due_bills(today)["items"], width="stretch")
    st.caption("Đây là dữ liệu demo cho giao tiếp Chat. Chưa có màn hình sửa/xóa giao dịch hay xác nhận thanh toán; không phải toàn bộ đồ án.")
