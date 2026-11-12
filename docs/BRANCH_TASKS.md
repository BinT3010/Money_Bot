# Branch assignments for agents

## How to use this guide

Run `git branch --show-current`, read root AGENTS.md and CONTRIBUTING.md, then
follow only the assignment matching that branch. These are planned tasks, not
claims that the features are complete or measured. Inspect the current code and
existing tests before editing: many backend capabilities already exist.

Java Android + Python FastAPI under MONEY_BOT is the supported product. Leave FE,
CHAT and Reminder reference logic alone. The backend implementation is in
MONEY_BOT/backend/moneybot; root backend modules are compatibility imports.
Do not start a parallel implementation or change language/framework/dependencies.

### Shared boundaries and delivery

- Work on the named branch, commit cohesive changes, and push that branch normally.
  Prepare a PR targeting main. Do not merge, delete/reset/synchronize branches,
  force-push, or push main without explicit authorization for the action.
- Check remote main before starting. If it has advanced, report the need to update
  your assigned branch; synchronization needs authorization under AGENTS.md.
- Domain files named below are primary ownership, not permission to modify every
  file. Shared files include MainActivity, ApiClient/HttpExecutor, ChatScreen,
  StatsScreen, models/parser/storage, app/dependencies, and integration tests.
  Coordinate overlapping edits and document cross-branch dependencies in the PR.
- Preserve existing endpoints/fields, migration compatibility, integer VND, category
  codes, account isolation, transaction/idempotency behavior and confirmation.
  Contract/schema changes require a separate team decision. Do not invent fields
  if existing endpoints already provide the needed values.
- Run backend `python -m pytest -q` from MONEY_BOT/backend. Run applicable suites
  with `python evaluation/run_eval.py --strict --suite ... --out <temporary-dir>`
  from MONEY_BOT. For Java changes run the documented real-server integration
  tests, debug build and lint. Use temporary databases; report actual checks.
- Completion means the assigned flow works through Java -> API -> SQLite, relevant
  regressions pass, and affected docs are current. Missing tools/data/physical
  trials remain explicit blockers. Do not change acceptance thresholds or label
  unmeasured objectives as passed. No background push notifications, banking,
  real payments, currency conversion, or unrelated account features.

## feature/bills — finish the Java recurring-bill workflow

### Existing baseline
Backend already supports schedule editing/stopping, actual payment amounts,
paging/history, cross-month reminders and obligation reconciliation. Java currently
creates/lists bills and pays/links transactions, but does not expose all that work.

### Assigned work
- Extend BillsScreen with edit and stop-schedule actions using existing PUT/DELETE
  contracts. Show the final active month clearly; preserve paid-period history.
- Add actual-amount entry when creating a payment; use existing amount_vnd support.
  Keep linked-transaction validation and confirmation; never create a second charge
  for an already-recorded payment.
- Expose existing limit/offset/history paging so older periods are reachable.
- Present the full existing obligation explanation: window, included items,
  remaining budget, missing data and separately excluded previous/next-month items.
  Reminders remain visible on open/refresh, never promise background delivery.
- Change backend bills.py/routes/bills.py only for a demonstrated missing behavior
  within these flows. Do not rewrite working schedule or payment algorithms.

### Acceptance
Cover edit/stop/resume where supported, paid history, actual vs expected amounts,
linking/retry without duplicates, page navigation, day 31/leap year/month boundary,
missing budget and excluded obligations. Run bill/payment/schedule/paging/reminder/
obligation tests and suites 3.1–3.4. Update Java integration tests for the new actions.

## feature/budget — finish budget explanations and monthly statistics

### Existing baseline
Backend already calculates three-month suggestions with basis, category warning
messages and monthly history. Java displays budget inputs and a short suggestion
reason, with limited rendering of the richer API output.

### Assigned work
- Render existing suggestion basis and history, scaling/budget constraint and
  unallocated amount. Keep suggestions as an editable preview until confirmation.
- Surface current category warning messages, threshold and usage state using API
  output, including UNSET versus explicit zero, exact 100%, and overspending.
- Extend Java statistics with monthly history/table and a simple native chart
  using existing API data. Do not add chart dependencies or recompute financial
  totals on Android. Coordinate StatsScreen changes with feature/merchant.
- Preserve month selection, edit/delete refresh, and the existing budget contract.
  Primary backend ownership is budget.py/routes/budget.py; no schema redesign.

### Acceptance
Cover insufficient history, zero history, budget scaling/rounding, unused budget,
zero/unset limits, custom thresholds, exact boundaries and refresh after edits.
Run budget tests, suites 2.1–2.4 and 1.4, plus Java integration coverage of preview,
confirmation and monthly display. Never assert 1.4 statistical accuracy from a UI
screenshot; retain independently computed expected totals.

## feature/merchant — carry merchant data through the supported UI

### Existing baseline
SQLite migration, backend transaction fields and draft models support merchant.
Java draft and transaction forms do not expose it; OCR currently does not reliably
extract a separate merchant field.

### Assigned work
- Add merchant display/editing to Java draft and transaction flows using existing
  fields. Keep description and merchant distinct. Coordinate ChatScreen/StatsScreen.
- Preserve absent/null (keep existing value on transaction edits) versus empty
  string (clear it), as documented by the current transaction API.
- Trace merchant from draft confirmation to stored transaction and reopened edit
  forms. Preserve legacy databases and idempotency fingerprints when omitted.
- Do not guess merchant from category keywords or classify solely by merchant name.
  Merchant extraction from receipt text belongs to feature/ocr-receipt; coordinate
  that interface rather than implementing a competing parser here.

### Acceptance
Cover present/absent merchant, edit/clear/round-trip, length validation, legacy
migration, duplicate request replay and account isolation. Run migration/core/API
regressions and add Java integration coverage for both draft and transaction forms.
Merchant accuracy is measured separately on labeled inputs, not inferred from these
behavioral tests. Do not change the database schema just to add UI controls.

## feature/ocr-receipt — complete receipt extraction and evidence

### Existing baseline
Local RapidOCR/ONNX image processing and text/scanned PDF handling already exist.
Receipt fields/ambiguity checks create drafts requiring confirmation. Existing
API/OCR tests are not the report's independent accuracy benchmark.

### Assigned work
- Improve extraction only for documented failures in supported image/PDF inputs;
  keep totals distinct from subtotal/cash/change, reject ambiguous values and retain
  manual correction. Missing/unreadable receipt dates must remain visibly unresolved.
- Add evidence-based merchant extraction into the existing optional merchant field.
  Coordinate field rendering with feature/merchant; do not change category policy
  to guess from store names, or confuse a suggested description with merchant data.
- Prepare labeled, non-identifying receipt/sentence fixtures and a held-out split.
  Tune on development inputs only. Reuse evaluation/run_eval.py and its result
  format for report objectives 1.1, 1.2, 1.3 and 1.5; coordinate harness/shared edits.
- Respect limits: supported image types, 10 MB upload, five-page PDF, pixel guard,
  no automatic foreign-currency conversion. Keep local inference and retry behavior.

### Acceptance
Include text PDF/scanned PDF/images, ambiguous totals/dates, foreign currency,
unsupported/corrupt/oversized inputs and no write before confirmation. Report
field/category metrics and processing latency on held-out samples, with failures
included. The report requires >=50 receipts and >=20 sentences with category
coverage and per-field thresholds; missing data is a blocker, never fabricate
results or retune on held-out answers. Run OCR/API/core tests and Java upload flow.

## experiment/evaluation — separate planned work

This branch was not synchronized by the feature-branch maintenance operation.
When its owner resumes it, obtain authorization to bring it to current main first.
Its intended scope is the shared evaluation harness, remaining G4 evidence and
cross-feature reporting. Do not invent six-person timing trials or turn acceptance
targets into achieved results. Coordinate receipt/merchant datasets with their
owners. No implementation work is authorized merely by this planning note.
