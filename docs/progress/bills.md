# feature/bills progress

Flow: schedule → reminders → payment/linking → period history and budget reconciliation.
This is a report, not new authority. The assignment is in docs/BRANCH_TASKS.md.

## Done (commit 8bb8861, on top of staging 51e224d)

The backend already had every contract this flow needs: edit, stop and resume (`PUT`/`DELETE /api/bills/{id}`),
`amount_vnd` on `/pay`, `limit`/`offset`/`history` paging and the full `obligations` object. This branch
adds no API, schema or shared-file changes. The work is in `BillsScreen.java` only, plus a new test class:

- Schedules: edit (name, amount, due day, start month, end month), stop (the server decides between deleting
  and keeping history; the result is shown) and resume (`end_month = null`).
- Payment: an actual paid amount field (defaults to the planned amount), or linking an existing utilities
  transaction from the current month and the period's month. When linked, the server uses that transaction's
  amount and records no second expense. Paid periods show the paid date and amount, and are flagged when the
  amount differs from the plan.
- History: 50 periods per page with "Tải thêm kỳ hóa đơn", plus a toggle between the default view (unpaid
  periods and 12 months of paid ones) and full paid history.
- Reconciliation: window, status (Đủ / Thiếu / Chưa đủ thông tin), budget total, confirmed spending,
  remaining budget, every period counted, difference, server message, excluded text, previous-month debt
  (count, total, the latest periods returned) and next-month periods in the window. These are shown but not added.
- Reminders: listed with period, due date and amount. The screen says they are computed on open/refresh,
  with no background notifications. Client dates use Asia/Ho_Chi_Minh.

## Verification (Windows 11, Python 3.12.13, portable JDK 17 / SDK from MONEY_BOT/.tools)

Run on clean commit 8bb8861:

- `.venv\Scripts\python.exe scripts\verify.py --out <scratch>`: backend 162 tests passed, strict evaluation
  passed (3.1 7/7, 3.2 14/14, 3.3 8/8, 3.4 8/8; 1.4, 2.1–2.4 also pass). **The Android step failed to start**:
  `[WinError 2]`, so the report is `passed: false` and not integration-eligible (see the request below).
- Same Android command run by hand, against `uvicorn app:app` on 127.0.0.1:18080 with an isolated `MONEYBOT_DATA`:
  `gradlew.bat testDebugUnitTest assembleDebug lintDebug --rerun-tasks` → BUILD SUCCESSFUL.
  BillsScreenTest 4/4 and IntegrationTest 7/7 pass.

BillsScreenTest (real screen, real server, expected values derived from the test inputs):
1. Due-today reminder, "Đủ" reconciliation with the remaining amount, payment with an actual amount that differs
   from the plan: one utilities expense of the actual amount is recorded, and the reminder disappears.
2. Linking an existing utilities transaction: still one transaction, and the period is PAID with the transaction's amount.
3. Edit due day; stopping an unpaid schedule deletes it; stopping one with paid history keeps it and ends at the previous month; resume.
4. 71 periods: paging 50 → 71; an old paid period is hidden from the default view and shown in full history;
   the previous-month debt count and total.

## Requests to the integrator (shared files)

- `scripts/verify.py` runs `["gradlew.bat"]` with `cwd=android`. On Windows, `CreateProcess` does not search the
  child's cwd, so the Android step fails before Gradle starts. Suggested fix: use the absolute path
  `str(ROOT / "android" / "gradlew.bat")`. Until then, full local verification on Windows cannot be eligible; CI (Linux) is unaffected.

## Remaining / limitations

- `/api/transactions` does not say whether an expense is already linked to a period, so the link list can
  offer a linked transaction. The server rejects it ("Giao dịch đã liên kết một kỳ khác."). Exposing this
  would be an API change and needs a team decision.
- Java tests use the real clock (the server has no injectable date over HTTP). Month-boundary and day-31
  behaviour stays covered by the backend tests and the 3.2 evaluation suite with fixed dates.
- Needs independent review. Not yet integrated into staging.
