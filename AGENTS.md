# Agent operating rules

## Read before editing
Read this file, README.md, CONTRIBUTING.md, and the relevant component documentation.
Inspect git status and the assigned branch. Preserve unrelated edits. If unexpected
changes or shared-file conflicts appear, stop the affected work and report them.

## Supported architecture and task boundaries
Java Android and Python FastAPI under MONEY_BOT are the supported application.
FE, CHAT, and Reminder are reference implementations: do not modify their logic
unless specifically assigned. Do not propagate changes across duplicate copies.
Money_BOT.tex defines product goals and acceptance criteria, not authorization to
implement every missing goal. Work only on the assigned task and components.
Architecture/language changes, client replacement, public API/schema changes,
dependency upgrades, and unrelated refactoring require an explicit team decision.
Do not spawn additional agents unless explicitly requested.

## Git and collaboration
Use the assigned branch; this refactor uses refactor/java-python-structure.
Create a task branch from current main only when no branch has been assigned.
Do not create branches for individual numbered subtasks. Normal pushes to the
assigned task branch are allowed. Merging, direct main pushes, force-pushing,
rewriting published history, branch deletion/renaming/resetting, and synchronizing
other branches require explicit authorization for that action.
Preserve feature/* and experiment/* as planned team work even when merged or behind.
Do not infer permission to delete from ancestry or an automatic merge setting.
Prepare reviewable changes and report checks before proposing integration.

## Financial and interface invariants
Keep calculations and financial persistence on the backend. Money is integer VND;
dates are yyyy-MM-dd, months yyyy-MM, timezone Asia/Ho_Chi_Minh. Keep the six current
category codes. Require confirmation before recording chat/OCR financial changes.
Preserve transaction atomicity, idempotency, account isolation, pending requests,
and database/pref locations. Never erase user data to make tests pass.

## Verification and reporting
Run relevant existing tests on isolated data. Report actual commands and outcomes;
separate historical results and acceptance targets from current verification.
Missing tools/network/dependencies are unresolved checks, never passing tests.
Do not alter expected evaluation answers to match observed output. Keep final
assessment data separate from rule/prompt tuning. Never invent human trials.
Never commit credentials, runtime databases, environments, or private input data.
Update affected documentation alongside implementation changes.
