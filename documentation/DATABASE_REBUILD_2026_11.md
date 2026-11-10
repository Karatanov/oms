# Registry rebuild from the 10 November 2026 source set

## Decision

Evolve the existing database rather than dropping it. `inspection_reports.project_id`
uses `ON DELETE CASCADE`: preserving only the inspection table is insufficient.
Keep users, inspection records, their dependent records and uploaded files, and
the project identities referenced by inspections. Do not recreate their IDs/UUIDs.

This document records a preflight, not a completed production refresh.

## Source ownership

| Source | Authoritative domain | Grain |
| --- | --- | --- |
| DB_Monitoring table, DB Table | Operational monitoring, bilingual names, municipality, sector, work status and progress | Subproject or lot, with repeated observations |
| UR III AT&PP, Batch 8/9 Allocation | Approved allocation and membership of each tranche | Allocation row; lots roll up to their base subproject |
| DB-Procurement Tables, DB6.1/6.2/6.3 | Dossiers, tender attempts and initial contracts respectively | Separate entity per dossier, tender attempt, contract |
| UR III Payments, SP Payment Monitoring | Cumulative monitoring indicators | Source identifier |
| UR III Payments, dated monthly payment sheets | Actual dated payments | Source sheet and source row, with contract identifier |

Do not turn cumulative totals into payment transactions, acts or uploaded documents.
Do not count repeated tender attempts as independent subprojects. Keep W, TS and CSC
contract purposes separate. The stable link is subproject/lot code, not row order.

## Verified preflight observations

- The supplied XLSX exports omit worksheet dimensions. `max_row=None` is not an
  empty workbook; readers must iterate the actual XML rows.
- DB Table has 158 populated rows and 154 distinct code strings across its cells.
  The code count is a cross-cell inventory, not a unique-subproject count.
- Batch 9 Allocation contains 71 distinct subproject/lot code strings.
- DB6.3 Initial Contracts has 199 populated rows including headers and 74
  distinct subproject/lot code strings. It must not define the entire project list.
- DB Table contains 54 cached Excel error cells. These include `AP115` (`#DIV/0!`)
  and `AH122` (`#VALUE!`). Missing/error values must not silently become zero.
- The current monitoring importer maps many columns by fixed index. A schema
  rebuild must preserve the source column/header provenance and validate those
  positions before accepting a replacement.

Reproduce the read-only inventory with:

```powershell
python tools/audit_registry_workbooks.py '<directory containing the four XLSX files>'
```

The JSON includes each file's SHA-256, worksheet inventory, detected identity
headers, source code inventory and exact formula-error coordinates. The tool does
not edit XLSX files and cannot write to a database.

## Required implementation sequence

1. Take and verify a recoverable MySQL backup and upload-volume backup. Record
   user/report/dependent-row counts and project UUID/code links before any write.
2. Stage all four immutable, fingerprinted inputs in one import batch. Validate
   typed values, formulas, hyperlinks, duplicate natural keys and source coverage.
3. Populate typed source-owned tables for allocations, monitoring observations,
   dossiers, tenders, contracts, payment summaries and payment transactions.
   Money uses exact decimal types; dates remain dates; missing values remain NULL.
4. Reconcile by stable codes and preserve existing project IDs. Keep missing
   referenced identities as historical records. Never cascade-delete inspections.
   A narrower monitoring/contract workbook must not deactivate allocation members.
5. Switch read models only after reconciliation. Publish one consistent batch,
   invalidate affected caches, and compare financial totals to source controls.
6. Verify users and inspection UUIDs, dependent rows, file checksums and links
   are unchanged, then verify Dashboard, Subprojects, Procurement, Finance and Map.

## Not yet performed

No production records have been deleted or refreshed by this preflight. Typed-table
migrations, coordinated batch import, backup/restore validation, production refresh
and post-import reconciliation remain implementation work.
