# Unattended staging workflow

Approved policy: reviewed, tested changes may integrate into staging. Main needs
one final explicit user approval. This is a workflow for running agents, not a
background agent service: GitHub Actions runs verification but does not spawn
coding agents. A session/controller must remain active to dispatch workers.

## Execution order

1. The foundation is integrated and verified on staging. Start feature workers
   using docs/BRANCH_TASKS.md. Each main release needs explicit user approval.
2. Start expense-capture, budget and bills from staging in separate worktrees.
   Evaluation harness/protocol work can run concurrently. Use existing named
   branches if they have work; merge staging normally, never reset or force-push.
3. Each worker owns one complete flow from docs/BRANCH_TASKS.md. Keep commits
   cohesive; avoid edits to another worker's files. Tests can be split into new
   feature-specific classes/modules instead of a shared growing integration file.
4. A different reviewer checks the exact feature head against the exact staging
   base. Review scope, contracts, failure handling, test oracle independence,
   data integrity and evidence. Log approval or concrete blockers with both SHAs.
5. Integrate sequentially. Build an actual merge candidate against the latest
   staging, then run full verification on that candidate. If staging/head changes,
   re-review affected changes and retest; never rely on an older green feature tip.
   Push the candidate to staging only if its base remains current. Keep branches.
6. Finally verify combined staging, attach logs/APK/results and prepare one PR
   targeting main. Do not merge that PR without the user's final approval.

## One verification command

With Python 3.12 dependencies installed, JDK 17, Android platform 35/build-tools
34.0.0 and ANDROID_HOME configured:

```sh
python MONEY_BOT/scripts/verify.py --out /tmp/money-verification
```

Use MONEY_BOT/.venv/bin/python on Linux or .venv/Scripts/python.exe on Windows.
The runner checks backend/API/OCR, existing strict evaluation suites and Android
real-server integration/build/lint using isolated data. It records revision, dirty
state, commands, outcomes and integration eligibility in verification.json. Use
an output directory outside the checkout. Partial `--backend-only` runs and dirty
working trees are never eligible for integration. Tests requiring humans remain
explicitly unmeasured. CI runs the same full command and uploads evidence/APK.

## Stop/retry rules

Fix routine test/build failures within assigned scope; at most three repair
attempts for the same failure before recording a blocker. Continue independent
work while one task is blocked. Do not change thresholds, expected answers or
held-out data to get green results. Credentials, missing real-person trials,
conflicting requirements, new public contracts/schema/dependencies or replacing
architecture require a decision; don't invent approval. A reviewer must be a
separate agent/reviewer execution from the author, not an author-written approval.

## Limits and enforcement

GitHub account currently has push access, not repository administration. CI and
these policies are present, but we cannot claim protected-branch enforcement.
The owner should require Verify application / verify on staging and main, and
restrict main merges to the maintainer. Never enable delete-branch-on-merge for
planned branches. Do not run untrusted PR code in pull_request_target or give the
verification job write credentials. CI success alone is not review approval.

## Current branch roles

main: approved product; feature work starts on the assigned staging-based branch.
staging: integrator-owned, tested combined work; final PR to main needs approval.
feature/expense-capture, feature/budget, feature/bills: three end-to-end workers.
experiment/evaluation: independent measurement tooling and reporting worker.
The retired merchant/OCR/foundation branch names must not be recreated. Workers
can start independently; evaluation final measurements wait for integrated code.
