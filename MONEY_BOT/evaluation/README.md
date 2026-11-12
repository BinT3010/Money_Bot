# Thực nghiệm Money Bot

Đo 16 việc con G1–G4 trong `Money_BOT.tex`. Các phép đo chạy qua **API thật** (FastAPI in-process, cùng code mà ứng dụng Android gọi). Mỗi tình huống dùng **ngày đánh giá cố định**, một tài khoản và một thư mục dữ liệu riêng.

```powershell
cd MONEY_BOT
.\.venv\Scripts\python.exe evaluation\run_eval.py            # chạy mọi bộ, ghi results\SUMMARY.md
.\.venv\Scripts\python.exe evaluation\run_eval.py --suite 3.2 # một bộ
.\.venv\Scripts\python.exe evaluation\run_eval.py --strict    # mã thoát 1 nếu có bộ chưa đạt ngưỡng
```

## Quy tắc

- **Đáp án chuẩn có trước khi chạy.** Giá trị mong đợi được tính tay hoặc lấy từ đặc tả, ghi trong `cases/` hoặc `data/`, rồi mới chạy. Không sửa đáp án để khớp kết quả.
- **Tách tập dev/test** cho phần có tinh chỉnh (phân loại, OCR): chỉ chỉnh quy tắc trên tập dev; báo cáo trên tập test.
- **Báo cáo trung thực:** tình huống lỗi, quá thời gian hay ngoại lệ đều tính là chưa đạt, không bị loại. Ngưỡng là mục tiêu nghiệm thu, không phải kết quả đã đạt.
- Mỗi kết quả ghi: mã tình huống, đầu vào, đầu ra mong đợi, đầu ra thực tế, đạt/chưa đạt và bằng chứng (HTTP status, thời gian ms từng bước).

## Cấu trúc

| Đường dẫn | Nội dung | Phụ trách |
|---|---|---|
| `run_eval.py` | Bộ chạy chung | Dũng |
| `cases/*.json` | Kịch bản khai báo (G2, G3) | Dũng |
| `suites/*.py` | Bộ có tính toán riêng (1.4 tổng hợp nhiều tháng; G1 độ chính xác) | 1.4: Dũng · G1/G4: Minh |
| `data/history/` | 5 bộ lịch sử ≥ 3 tháng (dùng cho 1.4 và 2.1) | Dũng |
| `data/receipts/`, `data/sentences/` | Hóa đơn và câu nhập G1 (dev/test) | Minh |
| `results/` | Kết quả chạy gần nhất (`<mã>.json`, `SUMMARY.md`) | sinh tự động |

## Kịch bản khai báo (`cases/*.json`)

```json
{
  "suite": "3.2", "title": "Nhắc trước 3 ngày, 1 ngày, đến hạn",
  "threshold": {"min_cases": 10, "pass_rate": 1.0},
  "today": "2026-11-12",
  "cases": [{
    "id": "3.2-01", "desc": "Còn 3 ngày", "today": "2026-11-12",
    "steps": [
      {"req": "POST /bills", "body": {"name": "Điện", "amount_vnd": 500000, "due_day": 15, "start_month": "2026-11"}, "save": {"bill": "id"}},
      {"today": "2026-11-13"},
      {"req": "GET /bills", "expect": {"reminders": [{"name": "Điện", "days": 2}]}, "max_ms": 5000}
    ]
  }]
}
```

- `req`: `METHOD /đường-dẫn` (không có tiền tố `/api`). `body`, `key` (Idempotency-Key cố định để thử phát lại).
- `status`: HTTP mong đợi (mặc định 200). `expect`: so khớp tập con. Dict chỉ xét các khóa được ghi; list phải cùng độ dài. Có các toán tử `{"$len": n}`, `{"$contains": [...]}`, `{"$any": true}`, `{"$absent": true}` và `{"$approx": x, "tol": t}`.
- `save`: lưu giá trị phản hồi (đường dẫn chấm, ví dụ `periods.0.period_id`) thành biến, dùng lại dưới dạng `{tên}`.
- Một bước chỉ có `{"today": ...}` thì đổi ngày đánh giá, dùng để mô phỏng làm mới qua nhiều ngày.

## Bộ Python (`suites/*.py`)

Khai báo `SUITE = {"suite": "1.4", "title": ..., "threshold": {...}}` và `run(ctx)`, trả về danh sách kết quả cùng định dạng: `id`, `desc`, `input`, `expected`, `actual`, `passed`, `problems`, `evidence`. `ctx.Session(today)` tạo một tài khoản mới; `ctx.match(expected, actual)` dùng cùng luật so khớp ở trên.

Riêng mục 4.2 cần 6 thành viên thử thật. Mục này không chạy tự động; quy trình và phiếu ghi do Minh chuẩn bị (M12).
