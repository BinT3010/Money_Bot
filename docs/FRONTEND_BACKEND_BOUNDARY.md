# Frontend/backend decision boundary

This inventory covers the Java Android screens and the budget slider changes
currently on `feature/ui`. Android may format values, draw charts, edit drafts,
and preview local form state. The backend remains authoritative for financial
facts, classifications, policy thresholds, validation, and persistence.

| Flow | Android currently does | Backend-owned decision or fact | Gap |
|---|---|---|---|
| Expense capture | Collects text/files, renders and edits the returned draft, asks for confirmation | Parsing, OCR extraction, category classification, validation, account ownership, and durable write | None identified in this UI pass |
| Budget and suggestions | Edits category limits, previews the sum/cap, shows recommendation and allocation bars | Three-month suggestion amounts and basis, spending warning levels, and rejecting saved limits whose sum exceeds the total | Acceptable `[a, b]` band for a necessary-category allocation is not returned by the API yet |
| Statistics | Draws the charts and lays out history rows | Totals, per-category values, history, remaining budget, and warning levels | None identified; chart geometry is presentation |
| Bills | Expands/collapses a card and colors the server status | Period generation, due/payment status, reminder data, obligation reconciliation, payment linking and persistence | None identified in this UI pass |
| Shared presentation | Formats VND with grouping separators; maps returned states to visual cues | Rejecting malformed/out-of-range money and dates, authorization, idempotency | Existing API validation remains authoritative |

## Budget allocation details

The backend already enforces the aggregate invariant in
`BudgetMixin.set_budget`: the sum of category limits cannot exceed the total
budget. The slider cap, typed-value cap, and allocation summary on Android are
immediate editing feedback; they mirror that invariant to avoid an avoidable
save rejection. The API check remains the final authority.
When no saved limits exist, Android also pre-fills an unsaved 20% share for each
of the five core categories and 0% for Other; it is not persisted until explicit
confirmation.

The additional “reasonable necessary expense” rule is different. The current UI
uses the requested temporary band `a = 0`, `b = 50%` of the total budget. This
lets users see blue in-range and red over-range behavior. It is a placeholder,
not the eventual policy. The next backend feature must define which categories
are necessary, calculate their lower and upper bounds from server-side data or
policy, and return those bounds with their basis. Android must then consume the
returned bounds and stop calculating them. Missing history or a category without
a range should not be represented as a numeric zero range.

## Next vertical feature

`feature/budget-range-feedback` owns that complete flow: backend range policy and
response, Android slider display, and isolated backend/Java regression coverage.
Its acceptance and branch instructions are in `docs/BRANCH_TASKS.md`. The exact
range formula and necessary-category set belong to backend code and tests; the
0–50% UI placeholder is not a product rule or an acceptance threshold.
