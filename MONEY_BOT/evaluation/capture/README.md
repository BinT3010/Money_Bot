# Expense-capture measurement fixtures

These are small **synthetic** development and held-out fixtures, not real-world
accuracy evidence or a claim that the report targets have been met. They contain
no real people, stores, invoices or private financial data.

- `data/dev.json`: six receipt transcripts and six sentences (one per category).
  Transcript cases bypass OCR intentionally and are identified as development
  parser checks. They never count toward image/PDF sample requirements.
- `data/heldout.json`: twelve rendered PNG invoices and twelve disjoint sentences
  (two per category). Labels and inputs are fixed before inference. Do not change
  expected labels or tune rules to improve performance on this split. If reused
  for tuning, retire it as a held-out set and obtain new evaluation data.
- Synthetic invoice layout/fonts and vocabulary cover only a narrow sample;
  they do not replace diverse receipt photos/scans or authentic language trials.

Run from MONEY_BOT with its Python 3.12 environment:

```sh
.venv/bin/python evaluation/capture/measure.py evaluation/capture/data/dev.json --out /tmp/capture-dev.json
.venv/bin/python evaluation/capture/measure.py evaluation/capture/data/heldout.json --out /tmp/capture-heldout.json
```

Use `.venv/Scripts/python.exe` on Windows and an appropriate output path outside
the checkout. Outputs include commit/dirty state, fixed evaluation date, per-case
expected/actual fields, errors, HTTP status and timings, field metrics and category
confusion matrices. Every case uses a separate chat session and real API handlers
with isolated SQLite storage; the benchmark never confirms or saves expenses.

`--require-field-targets` returns failure unless held-out coverage reaches >=50 real
image/PDF receipt inputs and >=20 sentences, at least five receipts and two
sentences per category, amount/date >=90%, merchant >=80% where provided, category
>=85% separately per source. API-only receipt latency is reported separately. Here
"real image/PDF input" distinguishes actual OCR from mocked transcripts, not
synthetic images from authentic invoices. This starter set intentionally has too
few samples, so field-benchmark acceptance remains **false**. Full `report_acceptance_met` is
always false for this field-only tool: it does not measure the confirmation flow
or end-to-end device latency. In-process API timings exclude
network transport and Android rendering; device-level latency remains unmeasured.

For additional labeled cases, use the same manifest shape with unique IDs,
`kind: receipt` and a relative image/PDF `file`, or `kind: sentence` and `text`.
Provide expected amount_vnd, ISO date, category_id, and merchant when the source
provides it. Merchant matching normalizes case/whitespace. Missing output counts
as incorrect; failed HTTP cases and timeouts must not be removed from reports.
Keep development and final manifests separate and annotate sample provenance.
Final evaluation ownership belongs to experiment/evaluation; do not overwrite
its shared harness, tracked summary, or predefined answers from this branch.

Objective 1.3 (correction/confirmation/no premature writes) is tested separately
by `tests/test_expense_capture.py` and `ExpenseCaptureTest.java`. This benchmark
only measures the suggested draft before manual correction.
