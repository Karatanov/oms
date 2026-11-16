# Budget representation reconciliation — 2026-11-16

## Status

Review, isolated database trial and guarded production repair completed. The
production after-state has been verified against the manifest. Import prevention
is implemented and tested locally; deployment status is reported separately.
These scripts are operator tools, not migrations; deployment alone does not
execute them.

## Evidence and scope

The read-only export contains 331 subprojects/parts. Source evidence is the raw
stored monitoring JSON, not API fallbacks derived from the saved amounts.
Only recognized monitoring/allocation workbook identities with matching source
lot and project code qualify. The prepared plan covers 56 distinct records:

- 47 stale UAH budget snapshots: original and EUR equivalent are reconciled at
  the existing saved rate. The rate, rate date and manual-conversion flag remain
  unchanged. No current exchange-rate lookup is performed.
- 9 differing legacy whole-UAH budgets, using the established positive-value
  integer truncation policy. No missing amount snapshot is created.
- `KH08_07#Lot6` is excluded: its existing equivalent does not reconcile with its
  frozen rate. Neither representation of this record is changed automatically.

The earlier 51-discrepancy classification required a saved snapshot. The broader
planner additionally identifies six differing legacy budgets without a snapshot,
and excludes the inconsistent-rate record: 51 + 6 - 1 = 56.

Manual conversions, non-UAH originals, invalid/absent source amounts, unexpected
workbook identities and ambiguous duplicate records are not silently repaired.

## Verification actually executed

- Backup: `/docker/oms/backups/registry-20261116T114612Z`.
- Full backup checksum and isolated restore verification passed (4 users,
  2 inspections, 332 project rows including the programme).
- Successful isolated trial:
  `oms_budgettrial_20261116114850` (scratch schema removed after success).
- SQL affected exactly 9 legacy budgets and 47 saved snapshots.
- Deterministic logical hashes of every other table and every protected column
  in the two target tables remained identical; target table schemas also match.
- Local post-trial verifier checked all 331 exported rows and precisely the
  planned changes to 56 records. Replanning after the trial returns zero changes.
- 9 planner regression tests and 5 existing classification tests passed.
- No frontend/backend compilation or browser test is claimed for these tools.

The trial caught two operator-tool defects before production use: incorrect
export character encoding (fixed with explicit utf8mb4 and binary file transfer),
and MySQL's automatic project `updated_at` mutation (now explicitly preserved for
historical reconciliation). Exact row-count assertions roll back a mismatched
plan rather than partially committing it.

## Tools and remaining work

1. `deploy/vps/export-budget-representations.sh` exports raw source and frozen FX
   metadata without user/report contents. Transfer generated exports as files,
   not through a PowerShell text pipeline.
2. `tools/plan_budget_snapshot_repair.py` generates reviewed SQL and a manifest;
   it never connects to the database. `--verify-after` checks an actual result.
3. `deploy/vps/verify-budget-repair-trial.sh` restores a verified backup into a
   new isolated schema, applies the SQL there and checks non-target invariants.
   A failed scratch schema is retained for diagnosis; the live schema is never
   selected by this trial script.
4. CI runs the offline planner regressions.

For any future application of a reviewed repair,
take/revalidate a fresh backup, repeat the trial if source data changed, compare
the live export to the reviewed before-state, run the guarded transaction and
verify the live result. Do not blindly replay an old plan.

## Production repair and import prevention follow-up

A fresh backup `/docker/oms/backups/registry-20261116T120725Z` was restored and
trialled successfully at `oms_budgettrial_20261116120748`. The trial records an
SHA-256 digest of the exact SQL. `apply-budget-repair.sh` checked that digest,
the successful backup restore/trial markers and exact equality between the live
export and reviewed trial before-state before applying the guarded transaction.

The live transaction changed exactly 9 legacy budgets and 47 snapshots. The
after-state verifier checked all 331 exported records: only the planned fields
changed. Replanning returns zero eligible changes. An independent production API
and raw-source audit reports 179 consistent saved snapshots, one excluded
inconsistent-rate snapshot (`KH08_07#Lot6`), 45 records without snapshots and 106
without raw source budgets. Missing snapshots/source budgets are not inferred
errors and have not been invented by this repair.

Verified code-level causes and corrections:

- `SubprojectRegistryRefreshService.saveProject` updated the legacy budget but
  left existing `project_amounts` budget snapshots stale. Refresh now locks the
  exact project's existing budget snapshot and updates original/equivalent
  amounts in the same import transaction. No new FX rate or snapshot is created;
  manually edited conversions, EUR originals and inconsistent frozen rates are
  protected. Duplicate budget snapshots fail the whole import transaction.
- A representative lot could overwrite the existing parent's budget. An
  existing parent budget is now changed only by a row with that parent's own code,
  never by a representative child lot. This does not introduce a new aggregation
  rule for newly synthesized parents; that separate policy still needs review.
- `MonitoringBudgetSnapshot.kt` parses original source cents with BigDecimal,
  reuses the saved rate and checks monetary column limits. Blank or invalid
  funding is not treated as a new zero snapshot; an explicit zero is supported.

Executed checks: 101 focused Kotlin tests (including four new budget conversion
regressions), 1,000 existing decimal reconciliation fixtures, 14 offline Python
tests, full server source compilation with Kotlin 2.3 and existing dependencies,
and remote Bash syntax checks. The direct compiler bypasses the local Gradle
loopback limitation; it is not claimed as a Gradle build. Existing unrelated
compiler warnings remain. No browser or live workbook re-import test is claimed;
the known invalid physical completion in the current online workbook must not
be bypassed merely to exercise this correction. Full CI/deployment results are
tracked separately. The wider formula audit is not claimed complete.

## Exact-corroboration follow-up: KH08_07#Lot6

The initially excluded record has independent corroborating values:

| Representation | UAH / EUR value |
| --- | ---: |
| Raw authoritative fundingTotal | 4,018,608 UAH |
| Legacy budget | 4,018,608 UAH |
| Saved original before repair | 3,959,211.85 UAH |
| Existing saved EUR equivalent | 83,721.00 EUR |
| Existing frozen UAH/EUR rate | 48.00000000 |

83,721 × 48 = 4,018,608 exactly. Thus the source amount already agrees with
the existing equivalent/rate pair; repairing the stale original does not require
guessing a new exchange rate or changing the EUR amount. The historical writer
of the stale original has not been identified from these observations.

Both the offline planner and runtime import rule now allow this narrow case:
an inconsistent old snapshot is eligible only when the approved source divided
by the saved rate matches the existing equivalent **exactly at cent precision**.
One-cent near matches do not qualify. Manual edits and EUR originals remain
protected even when the numbers happen to match.

Executed production sequence:

- Fresh full backup `/docker/oms/backups/registry-20261116T122322Z` passed restore
  verification; the isolated trial `oms_budgettrial_20261116122339` passed all
  non-target data, target schema and protected-column invariants.
- The exact verified plan was applied after checking the live before-state.
  It changed one snapshot, zero legacy budgets. The saved equivalent, frozen
  rate/date, manual flag and raw source payload remained unchanged.
- The after-state verifier compared all 331 records to the reviewed manifest.
  Replanning returns zero changes and zero skipped records.
- 103 focused Kotlin tests, 16 Python regressions and the existing 1,000 decimal
  fixtures passed, including exact-corroboration, idempotency, near-match rejection
  and manual/original-currency protection.
- Full server source compilation passed using the direct Kotlin 2.3 compiler;
  existing unrelated warnings remain. The independent live API/raw-source audit
  now finds 180 consistent saved snapshots and no conflicting snapshots. The
  other categories remain 45 without snapshots and 106 without raw source budgets.

The preceding import-prevention release `fd2f648` was independently confirmed
deployed at `/version`; all four workflows completed successfully. The new
exact-corroboration code release is tracked separately from the live data repair.
No claim is made that missing-source or missing-snapshot records are now populated,
or that the wider formula audit is complete.
