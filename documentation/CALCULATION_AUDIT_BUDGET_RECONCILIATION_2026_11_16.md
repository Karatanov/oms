# Budget representation reconciliation — 2026-11-16

## Status

Review and isolated database trial completed. **Production financial data has not
been changed by this repair.** These scripts are operator tools, not migrations;
deployment alone does not execute them.

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

Production application of the reviewed repair remains pending. Before applying,
take/revalidate a fresh backup, repeat the trial if source data changed, compare
the live export to the reviewed before-state, run the guarded transaction and
verify the live result. Do not blindly replay an old plan.

Prevention also remains open: `SubprojectRegistryRefreshService.saveProject`
updates the legacy budget but does not synchronize existing budget snapshots.
The representative-row policy for multi-lot parents needs separate verification
before changing import behavior. This checkpoint does not claim that the import
root cause has been corrected or that all financial calculations are audited.
