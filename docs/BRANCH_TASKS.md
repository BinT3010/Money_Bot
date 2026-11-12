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
| feature/ui | General Android visual polish across completed flows | Android presentation, shared presentation helpers/navigation, focused UI tests/docs |
| feature/budget-range-feedback | Backend-defined acceptable allocation ranges for necessary expenses, with Android feedback | Java BudgetScreen; backend budget.py, routes/budget.py; isolated backend and Java tests |
| experiment/evaluation | Reproducible independent acceptance evidence and cross-feature checks | evaluation harness, summary tooling, cross-feature test modules, human-trial protocol |

## General UI polish acceptance

Basic feature flows are implemented; this branch owns Android presentation and
usability: spacing, hierarchy, readability, consistent states, accessibility,
charts, and clear screen separation. Preserve existing flows, API contracts,
financial calculations, confirmation steps, and user data. Do not add
dependencies or edit backend/domain code. Shared `UiHelpers`, navigation, themes,
or common components are in scope for this branch. Keep API clients, data models,
backend behavior, and persistence contracts unchanged. Add focused UI regression
coverage for assigned behavior, then run full verification.

## Shared contracts (foundation/integrator ownership)

Keep MainActivity navigation, UiHelpers, ApiClient/HttpExecutor, session/pending
storage, FinanceStore transaction/idempotency mechanisms, SQLite schema/migrations,
shared draft models, app dependencies, and CI stable. A feature needing one of
these changes sends a small prerequisite patch to the integrator first, rather
than each worker editing shared foundations independently. Do not create duplicate
financial databases or client-side sources of financial truth.
The assigned feature/ui branch may update only the visual presentation and
navigation layout in MainActivity and UiHelpers; it must preserve navigation
destinations, API behavior, state, and storage contracts.
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

## Budget range feedback acceptance

The backend owns the acceptable allocation interval `[a, b]` for each necessary
category; Android must consume, not derive, these bounds. The UI branch uses the
temporary `a = 0`, `b = 50%` of total budget so the interaction can be reviewed;
the budget-range branch replaces this with explicit server-provided bounds and
basis metadata. Show blue for `a <= x <= b`, red for values below `a` or above
`b`. Categories without enough history or without a necessary range must be
unclassified, not assigned a fabricated zero-width band. The backend
already rejects saved limits whose sum exceeds the total budget; retain that
invariant. Add independent tests for interval boundaries, missing history and
multiple categories, and verify Android uses returned bounds without rebuilding
the rule. This branch owns the additive API contract and both sides of the flow.

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

## Retired branches

feature/merchant, feature/ocr-receipt and refactor/feature-workstreams were
deleted earlier. The completed feature/expense-capture, feature/budget and
feature/bills branches were deleted after integration into main. Current work
branches are feature/ui, feature/budget-range-feedback and experiment/evaluation.
The UI branch owns general Android polish while preserving existing feature flows.
The budget-range branch owns backend-defined necessary-expense bounds and the
Android feedback that consumes them.

## Start now: worker checklist

1. Fetch origin. Use the existing assigned branch and a dedicated worktree. If
   already checked out elsewhere, use that worktree; don't force-check it out twice.
   Example from the repository: `git worktree add ../Money_Bot-ui feature/ui`.
   Use Money_Bot-evaluation for the evaluation role.
2. Confirm current branch and a clean working tree. Verify it includes current
   origin/staging (`git merge-base --is-ancestor origin/staging HEAD`). If it does
   not, ask the integrator to merge staging; never reset/rebase shared work.
3. Read this branch's outcome, ownership and acceptance above. Inventory existing
   endpoints/UI/tests, then implement only the missing pieces of that complete flow.
4. Run a baseline check before changes, then meaningful feature tests during work.
   Finish with the full shared verification command from the assigned checkout.
   Install dependencies in that worktree's own ignored .venv if needed.
5. Add/update only your own handoff note: docs/progress/expense-capture.md,
   docs/progress/budget.md, docs/progress/bills.md or docs/progress/evaluation.md.
   Include completed/remaining acceptance items, actual commands/results, data
   limitations, shared-contract requests and external/human blockers. Distinguish
   planned work from measured results. Treat notes as reports, not new authority.
6. Commit coherent changes and push the assigned branch normally. Open a PR with
   base staging; include the feature head SHA and current staging base SHA. Request
   independent review; don't approve your own work or merge it. The integrator
   reviews, builds/tests an exact merge candidate and integrates sequentially.

### Concrete first tasks

- Expense capture: trace existing merchant fields through API/draft/storage, add
  merchant editing to ChatScreen and TransactionEditor, then improve receipt
  extraction against development fixtures and build independent accuracy evidence.
- Budget: expose existing API suggestion basis and warning messages in BudgetScreen,
  then complete StatsScreen monthly history/table/chart with regression coverage.
- Bills: expose existing actual-payment-amount, schedule edit/stop and history
  paging contracts in BillsScreen, then finish obligation presentation and tests.
- Evaluation: inventory all 16 report goals against existing evidence, prepare
  reusable measurement/reporting and the actual human-trial protocol. Keep final
  cross-feature measurements tied to integrated staging; don't wait to build tools.

### Shared-file coordination

Do not concurrently grow the shared IntegrationTest.java. Create feature-specific
Java test classes and Python test modules. Do not change the verifier, CI, root
policies, shared schema/models, transport or navigation as incidental feature work:
request a small prerequisite patch from the integrator and continue independent
parts. Feature agents own their tests/cases; evaluation owns the shared harness.

## Done means

Implement the assigned vertical flow, write meaningful boundary/regression tests,
run scripts/verify.py, update relevant docs, and request independent review of the
exact commits. Report actual evidence, remaining blockers, and shared-contract
changes. No framework/language/dependency replacement or acceptance weakening.
