# Kết quả kiểm thử phần Nhắc hạn hóa đơn định kỳ

Ngày kiểm tra: 12/11/2026, múi giờ người dùng Asia/Saigon.

## Môi trường và lệnh thực tế

- Môi trường chạy: Python 3 (thư viện chuẩn `unittest`, `calendar`, `datetime`, `re`), kết hợp mô-đun `classifier.py` của nhóm.
- File mã nguồn kiểm tra: `smart_money_bot_bill_reminder.py` và `test_bill_reminder.py`.
- Lệnh thực thi: `python -m unittest test_bill_reminder.py -v`.
- Kết quả cuối: **Ran 17 tests — OK (17 đạt, 0 lỗi)**.

| Nhóm kiểm thử | Số ca | Kết quả |
| :--- | ---: | :--- |
| K01 (`T01–T06`): Tách câu tạo lịch hóa đơn & kiểm tra hợp lệ các trường bắt buộc | 6 | Đạt |
| K02 (`T07–T08`): Tính ngày đến hạn thực tế cho tháng ngắn (`28, 29, 30` ngày) và khôi phục ngày `31` | 2 | Đạt |
| K03 (`T09–T15`): Xét trạng thái nhắc hạn trước `3 ngày`, `1 ngày`, `hôm nay`, `quá hạn`, `đã trả`, `chưa bắt đầu` | 7 | Đạt |
| K04 (`T16–T17`): Tổng hợp danh sách `due_bills` cho giao diện và xác nhận thanh toán chống ghi trùng | 2 | Đạt |
| **Tổng** | **17** | **17 đạt, 0 lỗi** |

Chi tiết các ca kiểm thử được định nghĩa trong `test_bill_reminder.py`:

- **Nhóm K01 (`T01–T06`):** Kiểm chứng câu mẫu trong `app.py` (`"Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026"`) tách đúng 4 trường và trả về trạng thái `cho_xac_nhan`; câu không ghi tháng bắt đầu tự động lấy tháng hiện tại với cờ `start_month_mac_dinh = True`; câu thiếu tiền hoặc thiếu ngày lặp trả về `can_bo_sung`; từ chối giá trị `bool` (`True`/`False`), số tiền âm, ngày lặp ngoài khoảng `1–31` hoặc tháng sai định dạng `YYYY-MM`.
- **Nhóm K02 (`T07–T08`):** Kiểm chứng hóa đơn có `due_day = 31` trả về ngày `30/11/2026` ở tháng 11 (30 ngày), tự động quay lại ngày `31/12/2026` ở tháng 12 mà không làm mất giá trị `31` gốc; kiểm tra tháng 2 năm thường (`28/02/2026`) và tháng 2 năm nhuận (`29/02/2028`).
- **Nhóm K03 (`T09–T15`):** Đặt ngày đánh giá cố định `12/11/2026` để kiểm tra chính xác các mốc: hạn ngày 15 báo `nhac_truoc_3_ngay` (`can_nhac = True`), hạn ngày 13 báo `nhac_truoc_1_ngay` (`can_nhac = True`), hạn ngày 12 báo `den_han_hom_nay` (`can_nhac = True`), hạn ngày 10 báo `qua_han` 2 ngày (`can_nhac = True`), hạn ngày 14 (còn 2 ngày) hiển thị `sap_den_han` nhưng không bật cờ nhắc lặp (`can_nhac = False`). Hóa đơn đã trả trong kỳ `2026-11` dừng nhắc hoàn toàn; hóa đơn có `start_month = "2026-12"` báo `chua_bat_dau`.
- **Nhóm K04 (`T16–T17`):** Hàm `due_bills` bỏ qua hóa đơn chưa tới tháng bắt đầu, cộng đúng tổng tiền các hóa đơn chưa trả (`670.000đ`) và lọc đúng danh sách câu nhắc; hàm `xac_nhan_thanh_toan` sinh đúng 1 bản ghi chi tiêu thuộc danh mục `"Hóa đơn tiện ích"` ở lần gọi đầu tiên và chặn hoàn toàn lần gọi thứ hai trong cùng kỳ tháng.

Ngoài ra, giao diện `smart_money_bot_bill_reminder.html` đã được kiểm tra bằng công cụ kiểm tra mã nguồn tĩnh (`check_web_app.py`) đạt kết quả `OK: no issues found`, đồng thời kiểm chứng trực tiếp ca đối chiếu ngân sách ngày `02/11/2026` (ngân sách `4.000.000đ`, đã chi `1.700.000đ`, 2 hóa đơn cần chuẩn bị `2.400.000đ` → báo thiếu dự kiến `100.000đ`).

## Những gì kết quả này chưa chứng minh

- Chưa kiểm thử tích hợp trực tiếp với file cơ sở dữ liệu SQLite thật của toàn nhóm khi nhiều mô-đun cùng ghi đồng thời; các ca kiểm thử hiện tại chạy trên cấu trúc dữ liệu trong bộ nhớ.
- Chưa đo lường trên tập câu nhập tự nhiên quy mô lớn của người dùng thật ngoài các mẫu câu quy ước; nếu người dùng diễn đạt câu tạo lịch theo cấu trúc lạ, hệ thống vẫn cần biểu mẫu (form) để người dùng điền hoặc chỉnh tay.
- Chưa có chức năng gửi thông báo đẩy (push notification) qua hệ điều hành hoặc email/SMS khi người dùng không mở ứng dụng.
- Phần xác nhận thanh toán trong mô-đun chỉ tạo bản ghi khoản chi và đánh dấu trạng thái trong sổ theo dõi, tuyệt đối không thực hiện giao dịch chuyển tiền ngân hàng thật.
