# Expense capture handoff — 2026-11-12

Branch: feature/expense-capture. PR base: staging (51e224d when work started).
No changes were merged into staging or main by this feature worker.

## Implemented

- Java draft rendering/editing and manual transaction forms expose merchant as a
  separate optional field. Existing merchant values reopen correctly; blank form
  values explicitly clear them. Missing/null values are shown as unset.
- Vietnamese text accepts explicit `; cửa hàng: ...`, `; cua hang: ...`,
  `; merchant: ...` or `; store: ...` metadata. Merchant-only follow-ups preserve
  description/category. Merchant text is excluded from intent and amount parsing.
- Receipt extraction accepts explicit seller labels or a seller header before a
  receipt title. It does not invent a merchant from an arbitrary product line.
  Identified merchant lines are excluded from category evidence.
- Missing, invalid or ambiguous receipt dates require correction; OCR no longer
  silently substitutes today. Text expenses may still propose today visibly.
- Negative totals (leading minus, accounting parentheses or trailing minus),
  competing totals and foreign currency require correction rather than saving a
  guessed positive VND amount. Cash/change/subtotal remain excluded.
- Draft correction, stale versions, upload replay, cancellation and no write
  before confirmation are covered through API/Java integration tests.

Shared prerequisite: one allowlist entry in money_chat/engine.py permits editing
existing merchant fields. No new model/column/schema migration, endpoint, transport,
financial store or navigation changes. Reference implementations were untouched.

## Verification evidence

Full local verifier at source f59e065 passed 186 Python tests, the 81 existing
strict evaluation cases, 10 Java integration tests, debug build and lint. The
subsequent null-rendering fix has independent review approval at a0c76ef; full
verification is repeated on the final PR head. Use current PR checks/artifacts
and verification.json for final-head evidence, not historical counts alone.

Commands (Python 3.12, existing pinned dependencies, JDK 17/SDK 35/build-tools34):

```sh
python MONEY_BOT/scripts/verify.py --out /tmp/capture-verification
python MONEY_BOT/evaluation/capture/measure.py MONEY_BOT/evaluation/capture/data/dev.json --out /tmp/capture-dev.json
python MONEY_BOT/evaluation/capture/measure.py MONEY_BOT/evaluation/capture/data/heldout.json --out /tmp/capture-heldout.json --require-field-targets
```

The final command **intentionally exits 1** for the starter sample because coverage
requirements are not met; it is not a regression-test failure. Benchmark source,
data layout, limits and matching rules are in evaluation/capture/README.md.

Held-out measurement at clean source a0c76ef (unchanged labels, no tuning on outputs):

| Source | Amount | Date | Merchant | Category |
|---|---|---|---|---|
| 12 synthetic OCR receipt images | 12/12 | 12/12 | 10/12 | 12/12 |
| 12 synthetic text inputs | 12/12 | 12/12 | 12/12 | 12/12 |

Two merchant whitespace recognition errors are retained, not patched using the
held-out answers. Maximum receipt API latency in that local run was 477.38 ms;
this excludes network transport and Android rendering. Measurements report commit,
dirty state, environment, per-case errors/timing and category confusion matrices.

## Remaining acceptance work / limitations

- The starter held-out set is deliberately small and synthetic. It does not meet
  >=50 receipt / >=20 sentence requirements, required receipt category coverage,
  or establish accuracy on diverse authentic photographs/scans/language.
- Expand independent labeled evaluation data with safe provenance; do not tune on
  the current held-out set. Never inflate the sample with duplicates just to pass.
- Full report acceptance remains false. Device/network latency needs an actual
  end-to-end measurement; no physical phone test was performed.
- The field benchmark does not measure confirmation/correction objective 1.3;
  those flows have separate API/UI regression coverage.
- Legacy unrecognized seller formats may remain unset; users can edit them. OCR
  errors are still possible and confirmation is always required.
- Final acceptance reporting/human trials remain with experiment/evaluation.

## Integration

Independent reviewer approved source a0c76ef against staging51e224d after requesting
and verifying signed-total, merchant-intent and benchmark-claim fixes. This is an
agent review, not an independent real-world accuracy study. Re-review affected
changes if staging advances. The integrator must verify the exact merge candidate;
the worker opens a staging PR and does not self-merge or delete branches.

## Requested warning and amount styling — 2026-11-13

Explicit user request expands presentation changes across the shared UI helpers
and budget/bills/statistics screens on this branch. Warning/error/pending cards
use a pale-red fill, red border and red text. Structured expense amounts are red;
positive available/remaining budget is green, negative balances red, zero neutral.
The total budget input is green. Loading/informational status resets old error
styles, and empty status cards are hidden. No income model, arithmetic, storage
or backend contract was added. These are presentation changes only. Existing
Python/evaluation/Java checks and APK build/lint passed during development; use
latest PR CI for the committed head. Updated build is installed with -r to retain
the existing emulator account and app data.

## Reconciliation with bills staging — 2026-11-13

Merged staging6eaf8c3 into this feature branch after the bills work landed.
Kept the complete staged bills UI: edit/stop/resume, actual payments/linking,
paging/history, reconciliation, reminders and timezone handling. Reapplied the
requested warning cards and amount colors to the new rendering methods; success
messages use the shared status helper so they do not remain hidden after an empty
status card. No staged bills behavior was intentionally removed.

The automatic engine merge retains both merchant editing and the new queued
expense behavior. Three cross-feature API regressions cover preserving different
merchants across queued expenses, editing the current merchant to cancel a queued
switch, and retyping the same expense with an updated merchant. Focused capture
and draft-switch checks:34 passed. Full verification and independent review are
required on this reconciled head before integration. This operation updates the
feature PR only; remote staging and main are not merged by the worker.
