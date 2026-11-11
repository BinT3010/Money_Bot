# Kết quả kiểm thử phần Chat

Ngày kiểm tra: 12/11/2026, múi giờ người dùng Asia/Saigon.

## Môi trường và lệnh thực tế

- Windows, Python 3.15.0.
- Interpreter cuối: `G:\PROJECT\TDTT\CHAT_PYTHON\.venv\Scripts\python.exe`.
- Streamlit 1.67.0; `pip check` trả `No broken requirements found.`
- Lệnh: `.\.venv\Scripts\python.exe -m unittest discover -s tests -v`.
- Kết quả cuối: **Ran 36 tests in 4.674s — OK**.

| Nhóm | Số ca | Kết quả |
|---|---:|---|
| T01–T20: luồng hội thoại theo đặc tả | 20 | Đạt |
| Lõi bổ sung: giữ nháp, nhập tay, phiên bản, chống trùng, lỗi và hợp đồng AI | 14 | Đạt |
| Streamlit AppTest: ghi/xác nhận/khôi phục; sửa form/phân loại lại/hủy | 2 | Đạt |
| Tổng | 36 | 36 đạt, 0 lỗi |

Các ca được liệt kê trong `tests/test_core.py` và `tests/test_ui.py`. T20 chủ động ghi thành công rồi giả lập mất phản hồi, tạo engine mới và kiểm tra kết quả vẫn chỉ có một giao dịch. Ca đồng thời kiểm tra bốn lệnh cùng khóa ở lớp SQLite, không phải kiểm thử nhiều người sửa cùng hội thoại.

Đã chạy thêm CLI với database tạm: `Ăn trưa 45k` → `xác nhận lưu` → `Tháng này chi bao nhiêu?` → `thoát`. Kết quả: tạo bản nháp, lưu một giao dịch 45.000đ và trả tổng 45.000đ qua một khoản trong tháng 11/2026.

Trong lần kiểm tra ban đầu, hai ca tiền thiếu đơn vị/tiền không hợp lệ phát hiện nội dung khoản chi bị mất khi hỏi lại. Đã sửa parser để giữ nội dung và chạy lại đạt. Kiểm tra form còn bổ sung việc phân loại lại khi sửa nội dung nhưng chưa chọn nhãn thủ công.

## Những gì kết quả này chưa chứng minh

- Chưa gọi API AI thật; HTTP và lỗi dịch vụ được mô phỏng trong test.
- Chưa đo độ chính xác hiểu tiếng Việt hoặc phân loại trên tập độc lập; không thể kết luận đạt 85%/90% từ 36 ca này.
- Chưa có thử nghiệm thời gian nhập với người dùng.
- AppTest kiểm tra widget và luồng chương trình, chưa phải kiểm tra hình thức bằng ảnh chụp mọi kích thước màn hình.
- Chưa có code của thành viên khác để chạy tích hợp thật. Backend/phân loại hiện tại là bản tham chiếu đã ghi rõ trong README.
- Chưa có toàn bộ ngân sách, OCR hoặc nghiệp vụ thanh toán của đồ án trong gói Chat.

Không dùng các dữ liệu thiết kế đã tinh chỉnh để công bố độ chính xác tổng quát. Khi ghép nhóm hoặc đổi bộ AI, cần chạy lại test hiện có và đánh giá thêm đầu vào mới.
