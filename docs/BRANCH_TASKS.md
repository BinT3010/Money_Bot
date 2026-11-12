# End-to-end feature assignments

Read AGENTS.md and AUTONOMOUS_WORKFLOW.md. Every feature owns its UI, API/domain
changes, regression tests and evaluation cases. Inspect existing capabilities
before implementing: backend bills/budget/merchant already have substantial code.
All feature PRs target staging. Each worker uses its own worktree.

| Branch | Outcome | Primary ownership |
|---|---|---|
| feature/expense-capture | Text/manual/image/PDF -> editable transaction draft including merchant -> explicit confirmation -> durable record | Java ChatScreen, TransactionEditor; backend routes/chat.py, routes/transactions.py, ocr.py, classification.py, classifier.py, money_chat/parser.py; capture tests/cases/data |
| feature/budget | History -> totals/charts -> suggestions with basis -> editable limits -> warnings | Java BudgetScreen, StatsScreen; backend budget.py, routes/budget.py; budget/statistics tests/cases |
| feature/bills | Schedule -> reminders -> payment/linking -> period history and budget reconciliation | Java BillsScreen; backend bills.py, routes/bills.py; bill tests/cases |
| experiment/evaluation | Reproducible independent acceptance evidence and cross-feature checks | evaluation harness, summary tooling, cross-feature test modules, human-trial protocol |

## Shared contracts (foundation/integrator ownership)

Keep MainActivity navigation, UiHelpers, ApiClient/HttpExecutor, session/pending
storage, FinanceStore transaction/idempotency mechanisms, SQLite schema/migrations,
shared draft models, app dependencies, and CI stable. A feature needing one of
these changes sends a small prerequisite patch to the integrator first, rather
than each worker editing shared foundations independently. Do not create duplicate
financial databases or client-side sources of financial truth.
TransactionEditor owns manual creation/edit/delete; StatsScreen only opens it.
ChatScreen owns expense draft editing and receipt/text entry. Keep existing API
URLs, fields and error semantics. One store transaction owns each confirmed write.

## Expense capture acceptance

Combine the earlier merchant and OCR assignments. Carry optional merchant through
receipt/text draft correction and manual/transaction editing; keep merchant and
description distinct. Existing transaction editing semantics: absent/null merchant
preserves its value, empty string clears it. No classification solely from store
name. Preserve validation, account isolation, retries, stale-draft protection,
image/PDF limits, currency rejection and no writes before confirmation.
Add isolated tests for each input path, correction, cancellation and persisted
round trips. Prepare non-identifying dev/held-out datasets for objectives 1.1,
1.2, 1.3, 1.5; report missing samples, failures and latency honestly. Do not tune
on final answers or claim report accuracy from regression tests. Existing OCR
and classification must be reused, not replaced with a parallel parser.

## Budget/statistics acceptance

Expose existing suggestion basis/history/scaling/unallocated amounts and keep
suggestions unsaved until confirmation. Display API warning messages and states,
including zero versus unset, exact 100%, custom threshold and overspending.
Complete monthly history/table and native chart from existing API output, with
no new dependency or independent Android financial totals. Test refresh after
transaction edits/deletes via the shared transaction contract; do not edit
TransactionEditor for chart work. Cover 1.4 and 2.1–2.4 with independent expected
values, plus Java flow tests.

## Bills acceptance

Expose existing edit/stop/resume, actual paid amount and paging/history contracts.
Show obligation window/items, remaining budget, missing information and excluded
previous/next-month items. Link existing payments without duplicate charges;
retain paid periods and day-31/month-boundary behavior. Reminders are on open/
refresh, never promise background notifications. Test 3.1–3.4 plus Java actions.
Consume existing transaction and budget interfaces; do not wait for new charts
or suggestion presentation in the budget branch.

## Evaluation acceptance

Keep predefined answers and held-out data independent of feature implementation.
Own harness consistency and the remaining G4 action-count/integration reporting.
Prepare the six-person manual-versus-bot timing protocol; actual participants are
required. No fabricated human trials or claims that all 16 objectives passed
because the available suites passed. Bind every report to code revision and
runtime environment. Harness work can run alongside features; final measurements
run on the integrated staging commit.

## Parked branches

feature/merchant and feature/ocr-receipt are preserved for history/planning only;
do not dispatch work there, delete them, or rewrite them. Their tasks are combined
in feature/expense-capture. Existing public names are retained intentionally.

## Done means

Implement the assigned vertical flow, write meaningful boundary/regression tests,
run scripts/verify.py, update relevant docs, and request independent review of the
exact commits. Report actual evidence, remaining blockers, and shared-contract
changes. No framework/language/dependency replacement or acceptance weakening.
