# Kết quả thực nghiệm

Chạy ngày 2026-11-12 bằng `evaluation/run_eval.py` (API thật, ngày đánh giá cố định theo từng tình huống).

| Việc con | Nội dung | Đạt / tổng | Ngưỡng | Kết luận |
|---|---|---|---|---|
| 1.1 | Chưa có bộ đo | – | – | Chưa đo |
| 1.2 | Chưa có bộ đo | – | – | Chưa đo |
| 1.3 | Chưa có bộ đo | – | – | Chưa đo |
| 1.4 | Tổng chi theo danh mục/tháng và lịch sử giao dịch, kể cả sau sửa/xóa | 10/10 | ≥10 tình huống, đạt 100% | Đạt |
| 1.5 | Chưa có bộ đo | – | – | Chưa đo |
| 2.1 | Gợi ý hạn mức từ 3 tháng lịch sử và ngân sách tổng, có căn cứ | 7/7 | ≥7 tình huống, đạt 100% | Đạt |
| 2.2 | Nhập ngân sách tổng, sửa hạn mức, phát hiện tổng hạn mức vượt ngân sách | 10/10 | ≥5 tình huống, đạt 100% | Đạt |
| 2.3 | Cảnh báo khi đạt 80% và khi đạt/vượt 100% hạn mức danh mục | 12/12 | ≥10 tình huống, đạt 100% | Đạt |
| 2.4 | Thay đổi mốc nhắc sớm và cập nhật cảnh báo | 5/5 | ≥3 tình huống, đạt 100% | Đạt |
| 3.1 | Lưu tên hóa đơn, tiền dự kiến, ngày hạn, chu kỳ tháng và trạng thái từng kỳ | 7/7 | ≥5 tình huống, đạt 100% | Đạt |
| 3.2 | Nhắc trước 3 ngày, trước 1 ngày và khi đến hạn hôm nay | 14/14 | ≥10 tình huống, đạt 100% | Đạt |
| 3.3 | Trạng thái kỳ; dừng nhắc kỳ đã trả, giữ lịch sử, tạo kỳ tiếp theo | 8/8 | ≥6 tình huống, đạt 100% | Đạt |
| 3.4 | Bảng tổng nghĩa vụ chưa thanh toán, ngân sách còn lại và chênh lệch có giải thích | 8/8 | ≥6 tình huống, đạt 100% | Đạt |
| 4.1 | Chưa có bộ đo | – | – | Chưa đo |
| 4.2 | Cần 6 thành viên thử thật; không chạy tự động (quy trình: M12). | – | – | Chưa đo |
| 4.3 | Chưa có bộ đo | – | – | Chưa đo |
