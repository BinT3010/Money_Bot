# Money Bot

Ứng dụng được hỗ trợ là **Android Java + Python FastAPI** trong `MONEY_BOT/`.
Java và Kotlin trong repository đều là ứng dụng Android; Java không phải web UI.
Xem [hướng dẫn chạy và kiểm thử](MONEY_BOT/README.md).

| Thư mục | Trạng thái |
|---|---|
| MONEY_BOT/android | Android Java được hỗ trợ |
| MONEY_BOT/backend/moneybot | Backend chính, một kho tài chính cho mỗi tài khoản |
| MONEY_BOT/evaluation | Bộ đo theo mục tiêu của báo cáo |
| FE | Android Kotlin/Compose tham khảo, không phải client đang được hỗ trợ |
| CHAT | Prototype Streamlit/terminal tham khảo |
| Reminder | Prototype nhắc hóa đơn tham khảo |

## Tài liệu và cách làm việc

Đọc [AGENTS.md](AGENTS.md) và [CONTRIBUTING.md](CONTRIBUTING.md) trước khi sửa code.
AGENTS.md quy định thao tác của agent; CONTRIBUTING.md quy định tích hợp nhóm.
Money_BOT.tex quy định yêu cầu sản phẩm và ngưỡng nghiệm thu. Mô tả Streamlit trong
báo cáo phản ánh thiết kế trước đây; kiến trúc triển khai hiện tại là Java/FastAPI.
README của các prototype chỉ áp dụng cho prototype đó. Hợp đồng API hiện tại được
khai báo trong backend/moneybot/routes và hiển thị ở /openapi.json, /docs.
Nếu yêu cầu và triển khai mâu thuẫn, báo cáo để nhóm quyết định, không tự đổi mục tiêu.

Không đồng bộ hoặc xóa các nhánh feature/* và experiment/* chỉ vì chúng đứng sau main.

## Phân công theo nhánh

Agent đọc [hướng dẫn công việc theo nhánh](docs/BRANCH_TASKS.md) sau AGENTS.md.
Ba luồng chính: expense-capture, budget, bills; evaluation đo và báo cáo độc lập.
Xem [quy trình staging](docs/AUTONOMOUS_WORKFLOW.md). Nhánh merchant/OCR cũ được giữ
làm tham khảo; công việc mới hợp nhất vào expense-capture.
