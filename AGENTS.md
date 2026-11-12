# Agent operating rules

## Read before editing
Read this file, README.md, CONTRIBUTING.md, docs/BRANCH_TASKS.md, docs/AUTONOMOUS_WORKFLOW.md, and relevant component documentation.
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
Use the assigned branch and its docs/BRANCH_TASKS.md assignment. One coherent
user flow per branch; no branches per numbered subtask. Use separate worktrees
for parallel workers. Normal pushes to assigned task branches are allowed.
The approved integration policy permits the integrator to merge reviewed work
into staging after full verification of the exact merge candidate. It may update
active feature branches from staging without rewriting their history.
Main remains review-gated: never merge/push main without the user's explicit final
approval. Branch deletion/renaming, force-pushing feature branches, architecture
replacement and scope expansion still need explicit authorization.
Preserve feature/merchant and feature/ocr-receipt as parked references; new expense
capture work belongs to feature/expense-capture. Do not assign workers to both.
Feature PRs target staging; the final release PR targets main. An independent
review must bind approval to the exact head and staging base commits. Changes to
acceptance thresholds/labels/held-out answers require explicit separate review.
Routine build/test fixes may be retried at most three times per unchanged failure;
then record the blocker and continue independent work. Never hide a failed check.

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
