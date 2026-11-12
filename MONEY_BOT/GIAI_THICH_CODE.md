# Giải thích project để đọc và sửa code

## Luồng một khoản chi

1. `MainActivity.chatAction` lấy câu người dùng nhập. `ApiClient` tạo UUID, lưu request đang chờ trên điện thoại rồi gọi API.
2. `app.chat_action` kiểm tra token để tìm đúng tài khoản. Client không được tự truyền đường dẫn DB hoặc user_id của người khác.
3. `FinanceStore.once` mở transaction SQLite, kiểm tra khóa yêu cầu. Nếu đã làm với cùng nội dung, trả lại đúng phản hồi cũ. Nếu cùng khóa nhưng khác nội dung, từ chối.
4. `ChatEngine.send` chạy máy trạng thái: hiểu ý định → trích tiền/ngày/nội dung → phân loại → kiểm tra thiếu → tạo nháp. `TeamClassifier` gọi cùng bộ từ khóa trong `classifier.py`; không có bộ phân loại riêng trên Android.
5. `snapshot` trả lịch sử, bản nháp, lỗi từng trường, trạng thái được xác nhận hay chưa và tổng quan ngân sách. Android hiển thị các dữ liệu này.
6. Nút xác nhận gửi mã nháp + phiên bản. `ChatEngine.confirm` từ chối nháp cũ, lưu pending, gọi `FinanceStore.commit`.
7. Tiền, biên nhận, trạng thái hội thoại và phản hồi được ghi trong cùng transaction. Tắt giữa chừng thì rollback; thử lại không ghi hai lần.

## Các file Python

| File/khối | Vai trò |
|---|---|
| `money_chat/models.py` | Sáu mã danh mục; cấu trúc Parsed/Draft; kiểm tra tiền nguyên dương, nội dung, ngày, tháng, ngày lặp. |
| `money_chat/parser.py` | Phân tích câu theo quy tắc; nhận bốn ý định; xử lý ngày tương đối, đơn vị tiền, câu thiếu và nhiều khoản. |
| `money_chat/engine.py` | Điều khiển hội thoại; send/merge/edit/evaluate/confirm/retry/complete. Không để parser tự ghi tiền. |
| `classifier.py` | Chuẩn hóa tiếng Việt, so khớp cụm từ, tính điểm; chưa rõ/hòa điểm thì không tự gán Khác. |
| `classification.py` | Ánh xạ nhãn tiếng Việt sang mã dữ liệu chung; giao diện dùng trong engine và OCR. |
| `money_chat/services.py` | Protocol và tên import tương thích; KeywordClassifier nay dùng chung TeamClassifier, không còn bộ luật khác. |
| `money_chat/storage.py` | Cơ sở SQLite kế thừa từ code Chat: expenses, bills, paid_periods, sessions, operations. |
| `finance.py — atomic/once/commit` | Mở một transaction cho cả yêu cầu; khóa SQLite chống ghi cạnh tranh; lưu kết quả theo khóa chống lặp. |
| `finance.py — overview/set_budget/suggest` | Ngân sách tổng/hạn mức, cảnh báo và công thức trung bình ba tháng có co ngân sách. Tính số nguyên để không sai do làm tròn float. |
| `finance.py — periods/pay` | Sinh kỳ từ tháng bắt đầu, kẹp ngày cuối tháng, xử lý nợ cũ; đánh dấu trả và tạo/liên kết chi trong cùng transaction. |
| `finance.py — obligations/reminders` | Cửa sổ 7 ngày giới hạn cuối tháng; nợ cũ riêng; danh sách nhắc đúng các mốc 3/1/0 ngày với ID ổn định. |
| `auth.py` | PBKDF2 600.000 vòng với salt riêng; token ngẫu nhiên chỉ lưu bản băm; hết hạn 7 ngày; giới hạn thử sai theo email. |
| `ocr.py — extract_text/image_text` | PDF có text đọc trực tiếp; PDF scan render; ảnh nhận dạng bằng RapidOCR/ONNX, ghép box cùng dòng. Giới hạn dung lượng/trang/pixel. |
| `ocr.py — receipt_fields` | Chọn giá trị trên dòng tổng, không lấy số lớn nhất tùy tiện; xử lý ngày, phân loại; không ghi DB. |
| `app.py` | FastAPI: schema request, dependency xác thực, endpoint gọi các dịch vụ; trả thông báo lỗi rõ ràng. |

`money_chat/ai.py` là adapter AI tùy chọn kế thừa để giữ tương thích và kiểm thử cũ; ứng dụng tích hợp mặc định không gọi nhà cung cấp AI. `money_chat/demo.py` chỉ phục vụ các kiểm thử hồi quy, không tự seed DB người dùng.

## Các file Java

| Khối | Vai trò |
|---|---|
| `ApiClient.call/execute` | Một hàng đợi HTTP ngoài UI thread; gắn Bearer token và Idempotency-Key; callback về main thread. |
| `ApiClient.retry` | Nạp lại request đã lưu và gửi nguyên key/body, không tạo request tài chính mới. Khi token hết hạn phải đăng nhập lại đúng tài khoản chủ yêu cầu. |
| `ApiClient.upload` | Giữ bản sao tệp đang gửi trong bộ nhớ riêng của app để có thể thử lại sau mất mạng; xóa sau phản hồi xác định. |
| `MainActivity.login/account` | Địa chỉ server, đăng ký/đăng nhập, đổi mật khẩu, đăng xuất. |
| `MainActivity.renderChat/draftForm` | Hiển thị hội thoại, biểu mẫu sửa, lỗi và nút xác nhận/hủy theo trạng thái backend. |
| `MainActivity.stats/transactionForm` | Tổng chi, phân bố, danh sách; chỉnh sửa/xóa khoản đã lưu qua API. |
| `MainActivity.budget` | Nhập ngân sách và hạn mức; tải gợi ý vào form, chưa tự lưu. |
| `MainActivity.bills/payForm` | Hiển thị kỳ nợ và nhắc hạn; chọn kỳ để thanh toán hoặc liên kết giao dịch có sẵn. |
| `IntegrationTest.java` | Client Java gọi Python thật; kiểm tra retry sau lỗi, phục hồi client, đăng nhập và hiển thị Chat trong Robolectric. |

## Quy tắc quan trọng

- Chỉ dùng mã `food`, `shopping`, `transport`, `utilities`, `entertainment`, `other`. Mã `bills` cũ của Kotlin không còn dùng làm danh mục.
- Tiền là số nguyên VND, ngày `yyyy-MM-dd`, tháng `yyyy-MM`.
- Trạng thái ngân sách: UNSET (chưa đặt), NORMAL, WARNING, REACHED (đúng 100%), OVER (vượt). Hạn mức 0 có chi dương là OVER.
- Không lưu từ OCR hoặc câu chat khi chưa xác nhận. Sửa nháp tăng phiên bản, nút xác nhận cũ không còn hiệu lực.
- Một kỳ hóa đơn chỉ gắn một giao dịch; một giao dịch không trả cho hai kỳ. Liên kết yêu cầu khớp tiền, ngày và danh mục hóa đơn.
- Mọi màn hình đọc API; Android không tính một tổng chi khác và không giữ kho tiền độc lập.
