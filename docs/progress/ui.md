# feature/ui progress

## Scope

General Android visual polish across existing screens. Keep API, financial
calculations, confirmation behavior, and persistence unchanged. UI work is on
the Java Android app under `MONEY_BOT/android`.

## Implemented

- Replaced the full-width horizontal tab row with a compact bottom navigation bar
  that shows the selected destination. Tab changes rebuild the page shell so its
  title and selected state stay aligned with screen content.
- Added consistent page titles, light system bars, rounded controls, readable
  inputs, and a shared color palette while preserving existing screen actions.
- Added a category-spending donut chart to Statistics beside the existing
  six-month bar chart. Both charts use values already returned by the API; the
  client does not calculate budget or transaction totals.
- Statistics shows warning states through a thicker red pulsing progress bar
  instead of repeating the server warning sentence. The pulse stops when the
  bar leaves the screen; non-warning bars use the shared chart palette.
- The first tab is now “Thêm khoản chi”. Its content is limited to a manual
  entry button and a chat box with text entry, send, and receipt-image/PDF import.
  Invalid assistant/error transcripts are omitted; valid and OCR drafts remain
  editable and require explicit confirmation.
- Statistics monthly history uses compact month cards with transaction count,
  total, and category breakdown on fewer lines. The manual-create action exists
  only on the first tab; Statistics retains editing from existing transaction rows.
- Budget constraints include a warning-threshold slider and category allocation
  sliders bounded by the current total budget. Edits stay local until the existing
  save action; warning levels and usage bars continue to come from the server.
  Tapping a category usage row focuses its limit field.
- Budget allocation editors show a blue recommendation bar and a provisional
  range from zero to 50% of the total budget. The editable allocation is blue
  inside that band and pulses red above it. The bounds are a UI-only placeholder;
  `feature/budget-range-feedback` will replace them with backend-provided ranges.
- Numeric inputs group thousands with commas while editing, and money displays
  use comma grouping with decimal points for fractional averages. Grouping commas
  are stripped before integer VND values are sent to the backend.
- The Bills tab now shows only the create-schedule action and one list of bill
  periods. Each card shows the name and amount only, green when paid and red when
  unpaid; dates, payment details, and payment/link actions appear after tapping.
  Reminder, reconciliation, and recurring-schedule summaries are not rendered
  here; reminder data/API behavior remains available for future notification work.
- Bill-card expand/collapse uses plus/minus cues. Budget status rows use concise
  amounts and color cues; the suggestion discard control is icon-only with an
  accessibility description.
- Added accessible labels for chart values and navigation actions.

## Verification

- `sh gradlew assembleDebug lintDebug --console=plain` with Android SDK
  configured and JDK 17: BUILD SUCCESSFUL.
- Installed the debug APK on `MoneyBot_API35` and visually inspected the first
  screen and Statistics chart/history layout. The first screen shows the form
  action and chat composer; warning replies are suppressed and valid draft
  confirmation is retained.
- Java unit tests and the full cross-platform verifier have not been run for
  this UI change yet.

## Remaining

- Update old Java UI tests to the redesigned screens and grouped number inputs;
  staging's full verifier currently fails those stale assertions. Then rerun the
  full verifier before requesting review.

## Latest budget-slider verification

- `testDebugUnitTest --tests com.moneyset.app.BudgetStatsTest.suggestedAllocationTurnsRedOutsidePlaceholderRange --rerun-tasks --console=plain`
  with an isolated FastAPI test database: BUILD SUCCESSFUL. The test checks the
  slider is blue at zero and at the 50% total-budget boundary, and pulses red
  above that temporary boundary.
- `assembleDebug lintDebug --console=plain`: BUILD SUCCESSFUL.
- Installed the resulting APK on emulator `emulator-5556` and restarted
  `com.moneyset.app`.
