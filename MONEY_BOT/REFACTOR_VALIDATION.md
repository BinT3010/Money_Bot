# Refactor validation — 2026-11-12

Branch: refactor/java-python-structure. Main and all planned feature/experiment
branches are unchanged. Validation uses isolated temporary databases.

## Backend

Python 3.12.16, exact existing backend/requirements.txt dependencies:
- Original main (b404caa), extracted under /tmp: 155 pytest cases passed.
- Refactored backend: 158 pytest cases passed, including real OCR/API coverage
  and three new legacy-import/database compatibility tests.
- Full OpenAPI documents compare equal: all 17 paths, request schemas and responses.
- All nine available evaluation suites pass strict mode. Baseline/refactor suite
  counts and acceptance outcomes match: 81/81 cases. Results written under /tmp,
  leaving tracked experimental evidence untouched. Seven report objectives remain
  unmeasured; passing the available suites does not mean all 16 objectives are met.
- One existing Starlette TestClient deprecation warning; no dependency changes.

## Android

Isolated Temurin JDK 17, existing Gradle 8.9 wrapper, Android platform 35 and
build-tools 34.0.0. Real Python test server on localhost:18080 with temporary data.
`testDebugUnitTest assembleDebug lintDebug --console=plain`: BUILD SUCCESSFUL.
- Seven Java integration tests passed, zero failures/errors/skips.
- Existing client/API/UI/retry coverage retained; added activity recreation and
  durable pending-upload store recovery coverage.
- Debug APK builds. Lint succeeds with 12 non-blocking warnings.
- No physical Android device test was performed. The tracked dist APK remains
  the previous artifact; the new APK is in android/app/build/outputs/apk/debug/.

## Other checks and delivery

Python AST parsing, route/request-schema AST comparison after import qualification,
and git diff --check pass. Source packaging and ZIP integrity smoke checks pass;
archive contains the new backend package/compatibility entrypoint and excludes
virtual environments and databases. Generated archives remain outside the repo.

No API/schema migration, architecture replacement, dependency upgrade, main push,
or deletion/update of planned branches is part of this change. Changes are committed
and published only on the refactor branch for maintainer review.
