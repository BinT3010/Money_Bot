> **Bản tham khảo:** ứng dụng được hỗ trợ nằm trong `MONEY_BOT/` (Java Android + Python). Không dùng tài liệu này làm hợp đồng tích hợp hiện tại.

# Phần Chat Smart Money Bot của Đức Duy

Bản Python phục vụ học, chạy thử và ghép nhóm. Có giao diện Streamlit và terminal; mặc định chạy bằng quy tắc, không cần tài khoản AI hoặc Internet sau khi cài thư viện.

## Đã có gì?

- Bốn ý định: ghi khoản chi, hỏi tổng theo tháng/danh mục, tạo lịch hóa đơn tháng, xem khoản cần trả.
- Hỏi trường thiếu, sửa bản nháp qua Chat hoặc form, chọn danh mục tay, hủy và xác nhận.
- Phân biệt bản nháp với dữ liệu đã lưu; từ chối nút/sự kiện phiên bản cũ.
- SQLite giữ lịch sử hội thoại, bản nháp và lệnh chờ qua lần khởi động lại.
- Chống ghi trùng ở database; kiểm tra lại khi mất phản hồi sau ghi.
- Mô-đun phân loại từ khóa và backend giao dịch/hóa đơn tham chiếu để Chat chạy độc lập.
- Bộ nối API AI tùy chọn cho câu chưa nhận diện; kiểm tra dữ liệu trả về có trong câu gốc. Chưa thử API thật.
- Bộ kiểm thử T01–T20 và kiểm tra bổ sung; giao diện có kiểm thử Streamlit AppTest.

**Đây là phần Chat và các thành phần hỗ trợ chạy thử, chưa phải toàn bộ đồ án.** Không có OCR, ngân sách/cảnh báo, màn hình sửa/xóa giao dịch đã lưu hay xác nhận thanh toán. Bảng hóa đơn chỉ đọc trạng thái từ backend tham chiếu. Không thực hiện thanh toán hoặc gửi nhắc nền. Khi các bạn bàn giao code thật, thay qua giao tiếp trong `money_chat/services.py`.

## Mở trong Visual Studio Code

1. Chọn **File → Open Folder**, mở `G:\PROJECT\TDTT\CHAT_PYTHON` (đúng thư mục này, không chỉ mở một file `.py`).
2. Cài extension **Python** của Microsoft nếu chưa có. Phần debug dùng extension Python Debugger của Microsoft.
3. Nhấn `Ctrl+Shift+P` → **Python: Select Interpreter** → chọn `CHAT_PYTHON\.venv\Scripts\python.exe`. Nếu chưa hiện, chọn Enter interpreter path và trỏ đúng file.
4. Mở **Terminal → New Terminal**, chọn PowerShell. Các lệnh dưới chạy từ thư mục dự án.

Trên máy Duy, môi trường `.venv` trong thư mục này đã được chuẩn bị khi bàn giao. Nếu chuyển sang máy khác hoặc giải nén ZIP, cần tạo lại môi trường; không sao chép `.venv` giữa máy.

## Cài đặt khi cần tạo lại môi trường

```powershell
cd G:\PROJECT\TDTT\CHAT_PYTHON
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Dự án đã được kiểm tra trên Python 3.15.0 và Streamlit 1.67.0 ở máy hiện tại. Phần lõi chỉ dùng thư viện chuẩn và cú pháp Python 3.10 trở lên; các phiên bản khác chưa được chạy lại tại lần bàn giao này. Không cần cài SQLite riêng.

Các lệnh dùng đường dẫn trực tiếp `.venv\Scripts\python.exe`, nên không cần chạy Activate.ps1 hoặc thay execution policy của PowerShell.

## Cách 1 — Chạy giao diện

```powershell
.\.venv\Scripts\python.exe -m streamlit run app.py --server.address 127.0.0.1
```

Mở địa chỉ local mà terminal in ra, thường là `http://127.0.0.1:8501`. Dừng bằng `Ctrl+C` trong terminal. Không chạy `python app.py` để mở giao diện.

Thử lần lượt:

1. Nhập `Ăn trưa` → bot hỏi số tiền.
2. Nhập `45k` → thấy bản nháp, chưa có giao dịch trong tab Dữ liệu đã lưu.
3. Nhập `đổi thành 50k` → bản nháp cập nhật.
4. Bấm **Xác nhận lưu** → có đúng một giao dịch 50.000đ và mã biên nhận.
5. Nhập `Tháng này chi bao nhiêu?` → lấy số liệu từ SQLite.
6. Nhập `Mua sách 80k` → chọn danh mục khi mơ hồ; thử Hủy để chứng minh không ghi dữ liệu.

Để thử dữ liệu chuẩn T12/T17: bật **Dùng bộ dữ liệu demo**, bật ngày giả lập và chọn **10/11/2026**, bấm **Thêm dữ liệu mẫu**. Tổng tháng 11 của dữ liệu mẫu ban đầu là 95.000đ; Ăn uống 75.000đ. Hỏi `Có khoản nào sắp phải đóng?` sẽ có phòng quá hạn và Internet sắp đến hạn, không nhắc điện đã trả. Bấm seed lần nữa không thêm trùng, nhưng giao dịch bạn tự thêm vẫn giữ nguyên và làm tổng thay đổi.

`data/demo.db` tách `data/chat.db`. Mở phiên Chat mới chỉ đổi hội thoại, không xóa giao dịch. URL chứa mã phiên giúp khôi phục bản nháp sau tải lại; phải mở cùng URL. Ứng dụng local đơn người dùng, chưa có đăng nhập và không thiết kế cho nhiều người chỉnh cùng một phiên đồng thời.

Trong form, **Cập nhật bản nháp** chỉ cập nhật thông tin đang xem. Đọc lại bản nháp, sau đó mới **Xác nhận lưu**. Nếu chưa cập nhật form, thao tác xác nhận vẫn áp dụng bản nháp đang được engine giữ, không phải các ô chưa gửi.

## Cách 2 — Chạy terminal, không cần Streamlit

```powershell
python -m money_chat --today 2026-11-10 --db data/terminal-demo.db --seed-demo
```

Lệnh này dùng Python đang có trên máy, không cần pip cho phần lõi. Hoặc dùng `.\.venv\Scripts\python.exe` để bảo đảm đúng interpreter đã chọn.

```text
Bạn: Ăn trưa
Bạn: 45k
Bạn: đổi thành 50k
Bạn: xác nhận lưu
Bạn: Tháng này chi bao nhiêu?
Bạn: thoát
```

Lệnh hỗ trợ: `hủy`, `xác nhận lưu`, `thử lại`, `kiểm tra kết quả`, `giữ bản nháp`, `hủy và chuyển`, `danh mục Khác`. Không dùng câu “ừ” làm xác nhận. Mỗi lần một khoản; câu có hai số tiền sẽ được yêu cầu gửi riêng từng khoản.

Bỏ `--today` để lấy ngày Việt Nam thật. `--session ten-phien` chọn hội thoại khác; `--db duong-dan.db` chọn database khác. Không nhầm ngày thử cố định với ngày thực tế.

## Chạy kiểm thử

Chỉ phần lõi, không cần thư viện bên ngoài:

```powershell
python -m unittest discover -s tests -p test_core.py -v
```

Toàn bộ, gồm giao diện sau khi cài requirements:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Mỗi dòng `... ok` là một ca đạt. Cuối cùng cần có `OK`; `FAILED`/`ERROR` là cần đọc lỗi, không được coi là đạt. Test tạo database tạm riêng và tự dọn, không dùng file dữ liệu của bạn. AI trong test được mô phỏng, không phát sinh cuộc gọi có phí.

Trong VS Code: mở biểu tượng **Testing** (bình thí nghiệm) → khám phá test → Run Tests. File `.vscode/settings.json` đã chọn `unittest` và `test_core.py`. Để kiểm tra giao diện, chạy lệnh toàn bộ ở terminal; hoặc đổi mẫu khám phá test thành `test_*.py` sau khi cài Streamlit.

Chạy riêng một ca, từ thư mục dự án:

```powershell
python -m unittest discover -s tests -p test_core.py -k T20 -v
```

## Debug để giải thích code

1. Mở `money_chat/engine.py`, đặt breakpoint (bấm lề trái) tại đầu `send`, `merge` hoặc `confirm`.
2. Mở **Run and Debug** → chọn **Chat terminal — ngày thử** → nhấn `F5`.
3. Gõ `Ăn trưa 45k` trong terminal; chương trình dừng ở breakpoint.
4. Xem Variables: `text` là câu nhập; `parsed` là trường trích được; `body` là phiên; `draft` là bản nháp.
5. `F10` đi qua một câu lệnh; `F11` đi vào hàm; `F5` tiếp tục.
6. Đặt breakpoint ở `SQLiteStore.commit` để chứng minh chỉ sau xác nhận mới tới phần INSERT giao dịch.

Có sẵn ba cấu hình F5: terminal, test lõi và Streamlit. File giải thích chi tiết là `GIAI_THICH_CODE.md`; các nhãn M/P/E/S/U/A/K trong source khớp mục trong tài liệu.

## Tùy chọn API AI

Không cần bật để chạy demo. API ở đây là endpoint **chat-completions tương thích hợp đồng JSON**: nhận `model`, `messages`, `temperature`; trả `choices[0].message.content` là chuỗi JSON. Không phải mọi dịch vụ đều có giao thức này; dịch vụ khác cần một adapter khác. Chưa chọn/chưa gọi nhà cung cấp thật tại lần bàn giao.

Thiết lập trong terminal nơi khởi động app, dùng giá trị thật của dịch vụ bạn được phép sử dụng:

```powershell
$env:MONEY_CHAT_AI_URL = "https://YOUR-ENDPOINT/chat/completions"
$env:MONEY_CHAT_AI_MODEL = "YOUR-MODEL-ID"
$env:MONEY_CHAT_AI_KEY = "YOUR-API-KEY"
.\.venv\Scripts\python.exe -m streamlit run app.py --server.address 127.0.0.1
```

Các chuỗi YOUR-… là chỗ điền, không phải dịch vụ sẵn dùng. Không đưa khóa thật vào source hoặc gửi kèm bài nộp. Mã không tự đọc `.env`; dùng biến môi trường như trên. Bỏ biến URL hoặc để trống để trở về chế độ quy tắc.

AI chỉ được gọi khi bộ quy tắc chưa xác định được ý định mới. Trong một bản nháp đang xử lý, bổ sung tiền/ngày vẫn dùng quy tắc hoặc form. AI trích bằng chứng có thật trong câu, không gọi SQL, không tính tổng, không xác nhận lưu. Khi API lỗi, bản nháp giữ nguyên và giao diện có đường nhập tay. Phân loại đang dùng baseline từ khóa; ghép mô-đun Cường qua `classifier` khi có.

## Giới hạn cần nói thật khi trình bày

- Bộ quy tắc hỗ trợ các cách nói minh họa, chưa hiểu mọi câu tiếng Việt, số tiền viết hoàn toàn bằng chữ hoặc mọi định dạng ngày. Dùng form khi câu không hiểu.
- Không có số đo độ chính xác 85%/90% hoặc giảm thời gian 50%. Test hành vi không thay nghiên cứu thực nghiệm trên dữ liệu mới.
- T20 kiểm tra giả lập mất phản hồi; AI được mock. Không được nói đã kết nối API thật.
- Backend hóa đơn chỉ là bản tham chiếu cho Chat. Chưa có màn hình thanh toán/nhắc nền/ngân sách/OCR trong gói này.
- Chống trùng bảo vệ cùng thao tác xác nhận; hai bản nháp mới giống nhau vẫn là hai khoản khác nhau. Không tự xóa một khoản chi hợp lệ chỉ vì trùng số tiền.
- Một người dùng; không chỉnh cùng phiên trên nhiều tab đồng thời. Đồng thời gửi cùng khóa ghi đã có kiểm tra, nhưng chưa xây kiểm soát xung đột nhiều người.

## Nguồn tài liệu chính thức dùng đối chiếu

- Giao diện hội thoại: [Streamlit st.chat_input](https://docs.streamlit.io/develop/api-reference/chat/st.chat_input).
- Mã phiên trên URL: [Streamlit st.query_params](https://docs.streamlit.io/develop/api-reference/caching-and-state/st.query_params).
- Chạy kiểm thử: [Python unittest](https://docs.python.org/3/library/unittest.html).
- Test và interpreter trong VS Code: [Python testing](https://code.visualstudio.com/docs/python/testing), [Python environments](https://code.visualstudio.com/docs/python/environments).
