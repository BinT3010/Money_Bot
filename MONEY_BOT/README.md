# Money Bot — Python + Java

Ứng dụng quản lý chi tiêu: Chat tiếng Việt, phân loại sáu danh mục, SQLite dùng chung, thống kê, ngân sách/cảnh báo, hóa đơn theo kỳ và OCR ảnh/PDF. Android viết bằng **Java**; server viết bằng **Python**. Không còn Kotlin hoặc dữ liệu tài chính giả lập trong ứng dụng này.

## Chạy bản source sau khi tải về

Bản nộp không kèm môi trường cài đặt. Cài Python **3.13 x64**, mở **thư mục MONEY_BOT** trong VS Code, rồi chạy lần lượt trong Terminal PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\SETUP.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\START.ps1
```

Giữ terminal chạy. Kiểm tra `http://127.0.0.1:8080/api/health`; tài liệu API tương tác ở `http://127.0.0.1:8080/docs`.

**Mở app Android:** cài `dist/MoneyBot-debug.apk` lên máy Android 8 trở lên hoặc emulator. Đây là APK debug phục vụ chạy thử, không phải bản phát hành Play Store.

- Emulator Android Studio: địa chỉ API `http://10.0.2.2:8080/api`.
- Điện thoại thật: cùng mạng LAN với máy tính; chạy `ipconfig` lấy IPv4 của Wi-Fi/Ethernet rồi nhập `http://IP_MAY_TINH:8080/api`. Nếu Windows Firewall hỏi, chỉ cho phép trên mạng riêng bạn tin cậy. Không dùng địa chỉ 127.0.0.1 trên điện thoại để gọi máy tính.
- Bấm **Tạo tài khoản**, nhập email và mật khẩu có chữ/số, tối thiểu 8 ký tự. Không có tài khoản/mật khẩu demo cài sẵn, không cần email xác minh.
- Nhập `ăn trưa 45k` → kiểm tra bản nháp → **Xác nhận lưu** → tab **Thống kê**.
- Gửi cùng yêu cầu hai lần hoặc mở lại app: hệ thống dùng khóa chống lặp; nếu mất mạng bấm **Kiểm tra yêu cầu đang chờ**. Không xóa dữ liệu app khi còn yêu cầu chưa rõ kết quả.

Lần đầu trên máy khác:

1. Cài Python **3.13 x64**.
2. Chạy `powershell -ExecutionPolicy Bypass -File .\scripts\SETUP.ps1`.
3. Chạy `START.ps1` như trên. Internet cần cho cài thư viện; Chat/phân loại/OCR sau cài chạy cục bộ, không cần khóa API AI.

## Dùng các chức năng

| Màn hình | Cách sử dụng |
|---|---|
| Chat | Ghi chi, hỏi tổng chi theo tháng, tạo lịch hóa đơn, xem đến hạn. Thiếu trường sẽ hỏi lại; có thể sửa/hủy. |
| Ảnh/PDF trong Chat | Chọn PNG/JPEG/WEBP/PDF, tối đa 10 MB và PDF 5 trang. OCR tạo bản nháp, chưa ghi tiền. Kiểm tra tổng tiền/ngày/danh mục rồi xác nhận. |
| Nhập giọng nói | Dùng dịch vụ nhận giọng nói có trên thiết bị Android; nếu máy không hỗ trợ, nhập bàn phím. Dịch vụ của thiết bị có thể cần mạng. |
| Thống kê | Xem theo tháng, tổng tiền và phân bố sáu danh mục; thêm/sửa/xóa khoản chi. Khoản đã liên kết hóa đơn được khóa sửa/xóa để giữ nhất quán. |
| Ngân sách | Lưu ngân sách tổng, hạn mức từng danh mục, mốc cảnh báo 1–99%. Để trống hạn mức khác với đặt 0. Đề xuất lấy ba tháng trước và cần xác nhận lưu. |
| Hóa đơn | Tạo lịch hằng tháng, xem các kỳ nợ cũ/hiện tại, xác nhận trả hoặc liên kết khoản chi đã ghi. Ngày 31 được kẹp cuối tháng ngắn. |
| Tài khoản | Đổi mật khẩu và đăng xuất. Đổi mật khẩu làm hết hạn các phiên hiện có. |

Ví dụ Chat: `ăn trưa 45k`, `tổng chi tháng 11/2026`, `tạo hóa đơn tiền mạng 200k ngày 15 hàng tháng từ tháng 11/2026`, `xem hóa đơn đến hạn`. Chat hỗ trợ bốn ý định cụ thể, không phải chatbot hỏi đáp mọi chủ đề.

Nhắc hạn 3 ngày/1 ngày/đúng hạn được hiển thị trong tab Hóa đơn khi mở hoặc làm mới; **không chạy thông báo nền khi đóng app**. Đối chiếu nghĩa vụ trong tháng là ngân sách còn lại, không phải số dư ngân hàng. Các kỳ nợ tháng trước được giữ và hiển thị riêng.

## Cấu trúc

```text
backend/
  app.py                 HTTP API và xác thực request
  auth.py                tài khoản, token, băm mật khẩu
  finance.py             SQLite chung, ngân sách, kỳ hóa đơn, thanh toán
  classification.py      adapter dùng bộ phân loại của nhóm
  classifier.py          bộ từ khóa của Cường, đã hợp nhất đường sử dụng
  ocr.py                 OCR ONNX cục bộ, PDF và trích trường
  money_chat/            engine/parser/model của Duy được tái sử dụng
  tests/                 regression + kiểm thử API/OCR/tính nhất quán
android/                 ứng dụng Java và Gradle wrapper
scripts/                 cài đặt, chạy, build, kiểm thử, đóng gói
examples/                hóa đơn giả lập có nhãn để thử OCR
dist/MoneyBot-debug.apk  APK sau build
```

Dữ liệu phát sinh nằm tại `backend/data/`. Mỗi tài khoản có một DB tài chính, các mô-đun của tài khoản đó dùng cùng DB; `accounts.sqlite3` quản lý đăng nhập. Không commit thư mục này. Sao lưu khi đã dừng server. Không tự nhập dữ liệu từ các bản mock hoặc SQLite cũ vì không biết thuộc tài khoản nào; code cũ trong TDTT được giữ nguyên.

## Build và kiểm thử

Để build từ bản nộp: cài JDK 17 và Android SDK theo hướng dẫn ngay dưới. Bản nộp không kèm `.tools/` hoặc `.venv/`. Sau khi cấu hình môi trường, chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\BUILD_ANDROID.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\TEST.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\TEST.ps1 -IncludeAndroid
```

Máy khác: cài **JDK 17**, Android SDK **Platform 35**, **Build Tools 34.0.0** qua Android Studio, đặt `JAVA_HOME`/`ANDROID_HOME`. Hoặc mở thư mục `android` bằng Android Studio, chọn Gradle JDK 17. Gradle wrapper 8.9 và plugin Android 8.7.3 đã được cố định. Nguồn tương thích: [Android Gradle Plugin 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes).

`TEST.ps1 -IncludeAndroid` mở một server kiểm thử riêng tại cổng 18080 và database tạm, chạy client Java thực vào Python bằng Robolectric rồi dừng server. Đây không phải kiểm thử trên điện thoại thật. Kết quả nằm trong `android/app/build/reports/tests/testDebugUnitTest/`.

Kiểm chứng ngày 12/11/2026: **63 kiểm thử Python và 5 kiểm thử Java tích hợp đạt**; `assembleDebug` và `lintDebug` thành công. Bao gồm OCR ảnh/PDF, client Java gửi ảnh tới Python, thao tác giao diện Chat/ngân sách/tạo và trả hóa đơn, chống gửi lặp và tách dữ liệu tài khoản. Android lint còn cảnh báo không chặn build (ví dụ chuỗi tiếng Việt chưa tách resource đa ngôn ngữ); Python có cảnh báo deprecation của thư viện TestClient. Chưa kiểm thử trên điện thoại vật lý.

## Giới hạn cần hiểu đúng

- OCR đã có nhận dạng thật, nhưng không cam kết độ chính xác trên mọi hóa đơn; mô hình mặc định tốt hơn với chữ Latin không dấu và số. Có nhiều tổng tiền/ngày hoặc đọc không rõ thì yêu cầu sửa. Không có tự chuyển đổi ngoại tệ.
- Đề xuất ngân sách giả định dữ liệu ba tháng trước được nhập đầy đủ; lịch sử chưa đủ thì yêu cầu nhập tay. Tổng đề xuất không vượt ngân sách.
- HTTP được bật cho demo LAN. Khi triển khai Internet phải đặt reverse proxy HTTPS và cấu hình vận hành phù hợp; bản này không tự công khai lên Internet.
- Không có luồng quên mật khẩu gửi email giả. Tài khoản thật hỗ trợ đăng ký/đăng nhập/đổi mật khẩu/đăng xuất; email reset chưa thuộc luồng demo M1–M8 này.
- Android Java giữ các luồng nghiệp vụ của FE nhưng giao diện native đã được viết lại, không sao chép nguyên giao diện Jetpack Compose.

## Nguồn tái sử dụng

Dựa trên [Money_Bot commit 577f763f9c04c1a470a7900efe8323cda0868e9d](https://github.com/BinT3010/Money_Bot/tree/577f763f9c04c1a470a7900efe8323cda0868e9d): tái sử dụng Chat Python, parser/validation/idempotency, bộ phân loại và kiểm thử; giữ các luồng FE Chat/Stats/Budget/Bills/Auth và tài nguyên mascot. Logic phân loại/ngân sách/hóa đơn trước đây tách riêng trong Kotlin đã chuyển về backend chung. README này hướng dẫn chạy source, không phải báo cáo học phần hoặc slide.
