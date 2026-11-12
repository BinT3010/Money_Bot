> **Bản tham khảo:** ứng dụng được hỗ trợ nằm trong `MONEY_BOT/` (Java Android + Python). Không dùng tài liệu này làm hợp đồng tích hợp hiện tại.

# Money Set — Android (Kotlin · Jetpack Compose)

App quản lý chi tiêu với trợ lý AI: ghi chi tiêu bằng chat/giọng nói/ảnh hóa đơn, ngân sách & cảnh báo hạn mức, hóa đơn định kỳ.

| | |
|---|---|
| Ngôn ngữ | Kotlin 2.4 (AGP 9.4, Gradle 9.8, JDK 25 của Android Studio) |
| UI | Jetpack Compose + Material 3 |
| Kiến trúc | MVVM (ViewModel + StateFlow) |
| API | Retrofit 2 + Gson + OkHttp |
| DI | Hilt |
| Bất đồng bộ | Coroutines + Flow |

## Chạy

1. Mở thư mục này bằng **Android Studio** → chờ Gradle sync (wrapper tự tải Gradle 9.8.2).
2. Chạy cấu hình `app` trên emulator/máy thật (minSdk 26).

Mở app sẽ vào **màn đăng nhập**. Luồng tài khoản: Đăng nhập · Đăng ký (đăng ký xong vào app luôn) · Quên mật khẩu → nhập mã 6 số + mật khẩu mới · Đổi mật khẩu và Đăng xuất ở tab **Cá nhân**. Phiên đăng nhập được lưu (DataStore) nên mở lại app không cần đăng nhập.

Ở chế độ mock:
- **Mọi tài khoản bắt đầu trống** (chưa có ngân sách, giao dịch, hóa đơn) và có dữ liệu riêng. Dữ liệu tài chính chỉ nằm trong bộ nhớ, tắt hẳn app là mất — backend thật sẽ lưu lâu dài.
- Quét ảnh hóa đơn chưa có AI thật: trả về bản nháp trống để tự nhập số tiền.
- Tài khoản demo có sẵn: `demo@moneyset.vn` / `Demo1234`. Tài khoản tự đăng ký được lưu trên máy (mật khẩu băm SHA-256), xóa dữ liệu app là mất.
- Không gửi email thật: mã quên mật khẩu hiện ngay trên màn nhập mã. Mã hết hạn sau 10 phút, sai 5 lần phải gửi lại, gửi lại sau 60 giây.
- Mật khẩu mới: tối thiểu 8 ký tự, có cả chữ và số.

Mặc định app chạy **dữ liệu mẫu trong máy** (`USE_MOCK_API = true` trong `app/build.gradle.kts`) nên không cần backend. Bộ bóc tách câu tiếng Việt offline hiểu được: `ăn trưa cơm tấm 55k`, `đổ xăng 80.000`, `tiền điện 1,2tr`, `hôm qua cafe 45`, `mua áo 250k 12/10`.

Để nối backend thật: đặt `USE_MOCK_API = false` và sửa `BASE_URL` (mặc định `http://10.0.2.2:8080/api/` = localhost của máy tính khi chạy emulator). Chạy trên máy thật thì thêm IP LAN vào `res/xml/network_security_config.xml`.

## Cấu trúc

```
app/src/main/java/com/moneyset/app/
├── core/            Định dạng tiền/ngày, bỏ dấu tiếng Việt, xử lý lỗi
├── domain/model/    Model nghiệp vụ (Transaction, Budget, Bill, ExpenseDraft...)
├── data/
│   ├── source/      FinanceDataSource (interface)
│   │   ├── remote/  Retrofit FinanceApi + DTO + mapper
│   │   └── fake/    Dữ liệu mẫu + ExpenseTextParser
│   ├── auth/        Đăng nhập: SessionStore (DataStore), AuthRepository, AuthInterceptor (Bearer token)
│   ├── repository/  FinanceRepository — cache StateFlow, nguồn sự thật duy nhất
│   └── ImageStore   Đọc/lưu ảnh hóa đơn
├── di/              Hilt modules (Network, Data)
└── ui/
    ├── auth/        Đăng nhập, đăng ký, quên/đặt lại mật khẩu, AuthNavHost, SessionViewModel
    ├── home/        Trang chủ: tổng quan tháng, cảnh báo nhanh, nhập nhanh
    ├── chat/        Chat với AI, thẻ xác nhận giao dịch, lịch sử
    ├── budget/      Phân bổ chi tiêu (donut), hạn mức danh mục, đề xuất hạn mức
    ├── bills/       Hóa đơn định kỳ, giả lập ngày, đánh dấu đã trả
    ├── stats/ profile/
    ├── components/  Thành phần UI dùng chung
    └── navigation/  NavHost + bottom bar
```

Linh vật đang là ảnh vector tạm `res/drawable/mascot.xml`. Thay ảnh thật: xóa file đó và thêm `res/drawable/mascot.png`.

## Hợp đồng API (cho backend)

`month` dạng `yyyy-MM`, `date` dạng `yyyy-MM-dd`, tiền là số nguyên VND.
`category` ∈ `food | shopping | bills | transport | entertainment | other`.
`type` (hóa đơn) ∈ `electricity | water | internet | rent | phone | other`.

Request gửi header `Authorization: Bearer <token>` khi đã đăng nhập. API ngoài `/auth/*` trả **401** thì app xóa phiên và quay về màn đăng nhập. Lỗi nên trả body `{ "message": "..." }` — app hiển thị nguyên văn message này.

| Method | Path | Body | Trả về |
|---|---|---|---|
| POST | `/auth/login` | `{ "email", "password" }` | `{ "token": "...", "user": { "name", "email" } }` — sai thông tin: 400/401 |
| POST | `/auth/register` | `{ "name", "email", "password" }` | giống login — email đã tồn tại: 409 |
| POST | `/auth/forgot-password` | `{ "email" }` | `{ "resendAfterSeconds": 60 }`, gửi mã 6 số qua email — email chưa đăng ký: 404 |
| POST | `/auth/reset-password` | `{ "email", "code", "newPassword" }` | 2xx — mã sai/hết hạn: 400 |
| POST | `/auth/change-password` (Bearer) | `{ "currentPassword", "newPassword" }` | 2xx — sai mật khẩu hiện tại: **400** (không dùng 401) |
| GET | `/transactions?month=2026-10` | | `Transaction[]` |
| POST | `/transactions` | `Transaction` (không id) | `Transaction` |
| GET | `/budgets/{month}` | | `Budget` |
| PUT | `/budgets/{month}` | `{ "totalLimit": 7000000 }` | `Budget` — tháng chưa có ngân sách thì GET trả `totalLimit: 0`, `categories: []` |
| PUT | `/budgets/{month}/categories/{category}` | `{ "limit": 700000 }` | `Budget` |
| GET | `/budgets/{month}/suggestions` | | `[{ "category", "limit" }]` |
| GET | `/bills` | | `Bill[]` |
| POST | `/bills` | `Bill` (không id) | `Bill` |
| POST | `/bills/{id}/pay` | `{ "month": "2026-10", "paidOn": "2026-10-19" }` | `Bill` — nên tự tạo giao dịch chi `bills` |
| POST | `/ai/parse-expense` | `{ "text": "ăn trưa 55k", "today": "2026-10-16" }` | `ExpenseDraft` |
| POST | `/ai/scan-receipt` | multipart: `image` (file), `today` (text) | `ExpenseDraft` |

```jsonc
// Transaction
{ "id": "tx-1", "amount": 55000, "note": "Cơm tấm", "category": "food", "date": "2026-10-16", "receiptUrl": null }
// Budget
{ "month": "2026-10", "totalLimit": 7000000, "categories": [{ "category": "food", "limit": 2250000 }] }
// Bill
{ "id": "bill-1", "name": "Điện EVNHCMC", "amount": 450000, "dueDay": 20, "type": "electricity", "paidMonths": ["2026-09"] }
// ExpenseDraft
{ "amount": 55000, "note": "Ăn trưa cơm tấm", "category": "food", "date": "2026-10-16", "confidence": 0.93 }
```

## Test

`ExpenseTextParserTest`, `AuthValidatorTest` (unit test JVM):

- Android Studio: mở file test → bấm ▶ cạnh tên class/hàm, hoặc chuột phải `app/src/test/java` → **Run 'Tests in java'**.
- Dòng lệnh (PowerShell, dùng JDK đi kèm Android Studio):

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  .\gradlew.bat testDebugUnitTest
  ```

  Kết quả HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
