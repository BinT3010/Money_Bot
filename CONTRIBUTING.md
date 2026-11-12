# Contributor workflow

Use Java Android and the moneybot Python package for supported product work.
Reference directories remain available for learning and attribution; do not maintain
parallel implementations by copying fixes into them.

1. Read AGENTS.md and agree on one task and its affected components.
2. Fetch current main before creating a new task branch. Continue on an assigned
   branch when one exists. Preserve the planned feature/* and experiment/* branches.
3. Make cohesive commits, retaining API behavior and documenting actual checks.
4. Push only the assigned task branch. Prepare a PR targeting main with the template.
5. The maintainer authorizes and performs integration after review and required
   checks. Branch deletion is a separate explicit decision, never an automatic step.

Do not rebase shared published branches or resolve teammates' changes by overwriting.
Coordinate changes to common API/storage/client code; document dependent PRs if used.

## Checks

From MONEY_BOT/backend with Python 3.12 and installed requirements:
`python -m pytest -q`. From MONEY_BOT run `python evaluation/run_eval.py --strict`.
Run Android `testDebugUnitTest`, `assembleDebug`, and `lintDebug` with the documented
JDK/SDK, using scripts/TEST.ps1 -IncludeAndroid for the real-client integration server.
Core fallback: `python -m unittest discover -s tests -p test_core.py` from backend.
A fallback does not replace API/OCR/Android validation. Record blocked checks.

Use temporary databases and keep user data out of tests and source archives.
Existing endpoint payloads/errors, SQLite schema/location, Android application ID,
preference keys, pending uploads, and retry keys are compatibility boundaries.
