# Phần Nhắc Hạn Hóa Đơn Định Kỳ Smart Money Bot của Xuân Phúc

Bản Python và giao diện HTML/JavaScript phục vụ học, chạy thử và ghép nhóm với phần Chat/Phân loại (`classifier.py`, `app.py`). Toàn bộ chạy bằng quy tắc thời gian xác định (Rule-based), không cần tài khoản AI hoặc kết nối Internet.

## Đã có gì?

- Tách câu tiếng Việt tạo lịch hóa đơn định kỳ (ví dụ: `"Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026"`) nối tiếp trực tiếp với `classifier.py`.
- Kiểm tra hợp lệ bản nháp hóa đơn định kỳ (`kiem_tra_hoa_don`): bắt buộc đủ tên hóa đơn, số tiền nguyên dương (VNĐ), ngày lặp (`1–31`) và tháng bắt đầu (`YYYY-MM`).
- Tính ngày đến hạn thực tế theo từng tháng (`ngay_han_thuc_te`): tự động lùi ngày `31` về ngày cuối tháng đối với các tháng có `28, 29, 30` ngày nhưng vẫn giữ nguyên ngày lặp gốc cho các tháng sau.
- Đánh giá trạng thái hóa đơn và lọc lịch nhắc (`kiem_tra_nhac_han`): nhắc trước `3 ngày`, trước `1 ngày`, `đến hạn hôm nay` và đánh dấu `quá hạn`; tự động dừng nhắc khi kỳ tháng đó đã thanh toán hoặc chưa tới tháng bắt đầu.
- Xuất bảng hóa đơn cần trả (`due_bills`) theo cấu trúc `{"items": [...], "reminders": [...], "total_due_vnd": ...}` khớp trực tiếp với lệnh `store.due_bills(today)["items"]` trong `app.py`.
- Xác nhận thanh toán theo kỳ tháng (`xac_nhan_thanh_toan`): chặn bấm xác nhận trùng hai lần trong cùng một kỳ và xuất bản ghi khoản chi thuộc danh mục `"Hóa đơn tiện ích"` theo đúng định dạng của `classifier.py`.
- Giao diện mô phỏng độc lập (`smart_money_bot_bill_reminder.html`) có bộ chọn giả lập ngày hệ thống và bộ kiểm thử tự động `test_bill_reminder.py` gồm 17 ca kiểm thử (`T01–T17`).

**Đây là mô-đun Nhắc hạn hóa đơn định kỳ và giao diện mô phỏng độc lập, chưa phải toàn bộ đồ án.** Mô-đun này không tự mở kết nối SQLite riêng hay gửi thông báo đẩy (push notification) chạy ngầm khi ứng dụng đã đóng. Khi ghép chung với nhóm, phần lưu trữ lâu dài đi qua `SQLiteStore` của `app.py` và phần đối chiếu ngân sách tổng hợp đi qua mô-đun của Cường.

## Mở trong Visual Studio Code

1. Chọn **File → Open Folder**, mở thư mục chứa dự án (đặt `smart_money_bot_bill_reminder.py`, `test_bill_reminder.py` và `classifier.py` trong cùng thư mục).
2. Cài extension **Python** của Microsoft nếu chưa có. Phần debug dùng extension **Python Debugger** của Microsoft.
3. Nhấn `Ctrl+Shift+P` → **Python: Select Interpreter** → chọn môi trường Python 3 trên máy (hoặc `.venv\Scripts\python.exe` nếu chạy chung thư mục `CHAT_PYTHON` của nhóm).
4. Mở **Terminal → New Terminal**, chọn PowerShell hoặc Command Prompt. Các lệnh dưới đây chạy trực tiếp từ thư mục dự án.

## Cài đặt môi trường

Mô-đun `smart_money_bot_bill_reminder.py` và `test_bill_reminder.py` chỉ sử dụng thư viện chuẩn có sẵn của Python (`calendar`, `datetime`, `re`, `unittest`), nên **không cần cài đặt thêm thư viện ngoài bằng `pip`** để chạy phần lõi và kiểm thử. Nếu chạy kèm giao diện Streamlit chung của nhóm (`app.py`), sử dụng môi trường `.venv` đã cài sẵn `streamlit`.

## Cách 1 — Chạy thử trong Terminal (không cần Streamlit)

```powershell
python smart_money_bot_bill_reminder.py
```

Hoặc dùng đường dẫn môi trường ảo của nhóm nếu đang mở trong `CHAT_PYTHON`:

```powershell
.\.venv\Scripts\python.exe smart_money_bot_bill_reminder.py
```

Thử nhập lần lượt trong terminal:

```text
Nhập câu tạo lịch: Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026
Nhập câu tạo lịch: Tiền điện 450k hạn ngày 20 hàng tháng
Nhập câu tạo lịch: Tiền phòng hạn ngày 5 mỗi tháng
Nhập câu tạo lịch: q
```

Kịch bản kiểm tra nhanh:

1. Nhập `Tiền mạng 200k hạn ngày 5 mỗi tháng, bắt đầu tháng 12/2026` → trích xuất đủ tên `Tiền mạng`, số tiền `200000`, ngày lặp `5`, tháng bắt đầu `2026-12`, trạng thái `cho_xac_nhan`.
2. Nhập `Tiền phòng hạn ngày 5 mỗi tháng` (thiếu số tiền) → trạng thái báo `can_bo_sung` và liệt kê `['amount_vnd']` trong `truong_thieu`.
3. Nhập `Tiền nước 100k mỗi tháng` (thiếu ngày lặp) → trạng thái báo `can_bo_sung` và liệt kê `['due_day']` trong `truong_thieu`.

## Cách 2 — Chạy giao diện mô phỏng HTML/JavaScript trên trình duyệt

Mở trực tiếp file `smart_money_bot_bill_reminder.html` bằng trình duyệt (Chrome, Edge) hoặc nhấp chuột phải vào file trong VS Code chọn **Open with Live Server**:

1. Bấm chuyển giữa 3 kịch bản mẫu ở thanh trên cùng: **Kịch bản Screen 4 (`19/10/2026`)**, **Ví dụ File Phân rã (`02/11/2026`)** và **Test Ngày lặp 31 (`29/11/2026`)**.
2. Đổi ngày bất kỳ tại ô **Giả lập ngày** ở góc trên bên phải khung điện thoại để xem trạng thái tự động chuyển giữa `Quá hạn`, `Đến hạn hôm nay`, `Còn 1 ngày`, `Còn 3 ngày` và `Chưa đến hạn`.
3. Bấm **Đánh dấu đã trả** trên một hóa đơn để thử hai chế độ: tạo khoản chi mới một lần hoặc liên kết với giao dịch đã có sẵn.
4. Tại bảng **Sổ Giao dịch Chi tiêu Đã xác nhận** bên phải, bấm **Xóa** vào giao dịch có nhãn `🔗 Đã gắn HĐ` để kiểm chứng hóa đơn tự động quay về trạng thái chưa trả.

## Chạy kiểm thử tự động

Chạy toàn bộ 17 ca kiểm thử (`T01–T17`) bằng `unittest` chuẩn của Python:

```powershell
python -m unittest test_bill_reminder.py -v
```

Mỗi dòng `... ok` là một ca kiểm thử đạt. Cuối cùng màn hình in ra `Ran 17 tests ... OK`. Nếu xuất hiện `FAILED` hoặc `ERROR` thì cần đọc thông báo lỗi để sửa, không coi là đạt.

Chạy riêng một ca kiểm thử bất kỳ (ví dụ `T07` kiểm tra ngày lặp 31 qua tháng thiếu):

```powershell
python -m unittest test_bill_reminder.py -k T07 -v
```

## Debug để giải thích code trên VS Code

1. Mở file `smart_money_bot_bill_reminder.py`, đặt breakpoint (bấm vào lề trái số dòng) tại đầu hàm `tach_lich_hoa_don`, `ngay_han_thuc_te`, `kiem_tra_nhac_han` hoặc `xac_nhan_thanh_toan`.
2. Mở mục **Run and Debug** (`Ctrl+Shift+D`) → chọn **Python Debugger: Current File** → nhấn `F5`.
3. Gõ câu `Tiền điện 450k hạn ngày 20 hàng tháng` vào terminal; chương trình sẽ dừng ngay tại breakpoint.
4. Quan sát khung **Variables** bên trái: xem `raw` (câu gốc), `folded` (câu đã bỏ dấu), `due_day` (ngày lặp), `due_date` (ngày hạn thực tế của tháng) và `days_left` (số ngày còn lại).
5. Nhấn `F10` để đi qua từng dòng lệnh, `F11` để đi sâu vào hàm con (`ngay_han_thuc_te` hoặc `kiem_tra_hoa_don`), và `F5` để chạy tiếp.

File giải thích chi tiết từng khối lệnh là `GIAI_THICH_CODE.md`; các nhãn `R01–R07` trong mã nguồn khớp trực tiếp với từng mục trong tài liệu.

## Giới hạn cần nói thật khi trình bày

- Chức năng nhắc hạn chạy hoàn toàn bằng quy tắc thời gian và biểu thức chính quy (Rule-based), không sử dụng mô hình AI.
- Bộ tách câu `tach_lich_hoa_don` chỉ hỗ trợ các cấu trúc câu thông dụng có từ chỉ ngày lặp (`hạn ngày 5`, `ngày lặp 20`) và số tiền chuẩn; nếu câu phức tạp không tách được, người dùng nhập hoặc sửa qua biểu mẫu (form).
- Hệ thống chỉ kiểm tra và hiển thị thông báo nhắc hạn khi người dùng mở hoặc làm mới ứng dụng; không có tiến trình chạy ngầm (background daemon) gửi thông báo đẩy ra màn hình khóa điện thoại khi đã đóng app.
- Dữ liệu trong `smart_money_bot_bill_reminder.py` xử lý trên cấu trúc `dict`/`list` trong bộ nhớ để ghép nối linh hoạt; việc lưu trữ bền vững xuống ổ cứng do lớp `SQLiteStore` của nhóm đảm nhiệm.

## Nguồn tài liệu chính thức dùng đối chiếu

- Tính số ngày của tháng và xử lý năm nhuận: [Python calendar.monthrange](https://www.google.com/search?q=https://docs.python.org/3/library/calendar.html%23calendar.monthrange).
- Kiểu dữ liệu ngày và phép trừ khoảng cách ngày: [Python datetime.date](https://docs.python.org/3/library/datetime.html#date-objects).
- Biểu thức chính quy tách mẫu câu: [Python re — Regular expression operations](https://docs.python.org/3/library/re.html).
- Chạy kiểm thử đơn vị: [Python unittest](https://docs.python.org/3/library/unittest.html).
- Kiểm thử và chọn môi trường trong VS Code: [VS Code Python testing](https://code.visualstudio.com/docs/python/testing).
