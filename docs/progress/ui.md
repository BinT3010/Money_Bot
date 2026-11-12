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
  entry button and a chat box. Invalid assistant/error transcripts are omitted;
  valid drafts still show their details and require explicit confirmation.
- Statistics monthly history uses compact month cards with transaction count,
  total, and category breakdown on fewer lines.
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

- Run Java unit tests and the full verifier before requesting review.
