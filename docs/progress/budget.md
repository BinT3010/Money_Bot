# feature/budget progress

Flow: history → totals/charts → suggestions with basis → editable limits → warnings.
This is a report, not new authority. The assignment is in docs/BRANCH_TASKS.md.

## Starting point

The branch was fast-forwarded to staging 072a940. The backend already had every contract this flow needs:
`GET /api/summary` (levels, percent, remaining, server messages), `GET /api/summary/history`,
`GET/PUT /api/budgets/{month}` and `GET /api/budgets/{month}/suggestions` (history, totals, averages, A, B,
factor, allocated, unallocated, missing inputs). Backend tests and the 1.4 and 2.1–2.4 evaluation suites
already existed and passed. This branch adds no API, schema, dependency or shared-file changes. The work is in
`BudgetScreen.java` and `StatsScreen.java`, plus a new test class and README rows.

## Done

- **BudgetScreen, warning states:** a block at the top loads `/summary` for the month. It shows the threshold in
  use, total budget, spending, and the remaining or overspent total. Each category shows its level label and the
  server message unchanged. A zero limit is shown as a set limit, unlike an unset one. WARNING, REACHED (exact
  100%) and OVER are styled as warnings. After saving, the screen reloads, so a custom threshold takes effect
  immediately.
- **BudgetScreen, suggestion basis:** "Gợi ý từ 3 tháng trước" fills the limit fields only. It also shows a
  table of the three months per category with the average, the server reason, the suggested limits,
  A, B, factor (scaled or kept), and allocated and unallocated amounts. Nothing is saved until "Xác nhận lưu
  ngân sách". "Bỏ gợi ý, giữ hạn mức đã lưu" discards it. When no suggestion is possible, the screen shows the
  reason, each missing input (saved budget total, or history starting at least three months back) and any
  history returned.
- **StatsScreen, history:** a native bar chart (a custom `View` with Canvas, no library) and a table for the
  last 6 months, both from `/summary/history`. The table shows month, total, count and non-zero spending per
  category. The chart has a content description listing exact values. Warning categories also show the server
  message. The transaction list now says when a month is empty. Editing and deleting still go through
  `TransactionEditor` (unchanged), and every view refreshes from the API after `host.load()`.
- The Android client computes no financial totals. It only formats server values. Averages with two decimals are
  shown as returned.

## Verification (Windows 11, Python 3.12, portable JDK 17 / SDK from MONEY_BOT/.tools)

Run against the working tree before committing:

- `uvicorn app:app --host 127.0.0.1 --port 18080` with an isolated `MONEYBOT_DATA` in a scratch folder, then
  `gradlew.bat testDebugUnitTest assembleDebug lintDebug --rerun-tasks` → BUILD SUCCESSFUL.
  BudgetStatsTest 4/4, BillsScreenTest 4/4, ExpenseCaptureTest 3/3, IntegrationTest 7/7. Lint reports no
  new issue types (the existing ApplySharedPref, OldTargetApi and DataExtractionRules remain).
- `python -m pytest -q` (backend): 198 passed.
- `python evaluation/run_eval.py --strict`: 1.4 10/10, 2.1 7/7, 2.2 10/10, 2.3 12/12, 2.4 5/5 (bills suites pass too).
  The regenerated `evaluation/results/*` files were not committed (evaluation owns them).
- `scripts/verify.py` was not run as a full eligible check. Its Android step fails to start on Windows
  (see the request in docs/progress/bills.md), so the Android part was run by hand as above.

BudgetStatsTest (real screens, real server, expected values worked out by hand from the inputs):
1. Exact 80% → WARNING, exact 100% → REACHED, 125% → OVER with the overspent amount, zero limit vs unset,
   remaining total. Raising the threshold to 90% via the form makes the 80% category normal. Overspending the
   total budget shows the overspent amount.
2. Suggestion with scaling (B/A = 0,7912, unallocated 2đ, fractional average 33.333,33đ). The limits are not
   saved until confirmation. Discarding restores the saved (empty) limits. Confirming persists exactly the suggestion.
3. Suggestion unavailable: both missing inputs are listed and no field is filled.
4. Six-month chart and table values (a 7th month is excluded). Edit and delete through TransactionEditor refresh
   the month total, the chart and the table.

## Remaining / limitations

- Java tests use the server's real date (`/api/health`). Fixed-date and month-boundary cases stay covered by the
  backend tests and the 1.4 and 2.1–2.4 evaluation suites.
- The chart shows monthly totals only. The per-category split per month is in the table, not stacked bars.
- Needs independent review. Not yet integrated into staging.
