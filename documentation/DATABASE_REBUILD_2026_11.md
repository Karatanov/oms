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

## Backup and staging implementation

`deploy/vps/backup-registry-rebuild.sh` creates a restricted SQL/uploads backup
and records protected identity links. `verify-registry-backup.sh` restores the SQL
into a new isolated database, compares protected counts and report links, then
removes only that scratch database on success. A failed restore is retained for
diagnosis. These scripts were executed successfully on 10 November 2026.

`tools/stage_registry_sources.py` generates SQL for the separate
`oms_registry_staging` schema. It retains typed cached values, source formulas
(including array formulas), Excel error markers and hyperlink targets, keyed by
immutable batch/file/sheet/row identities. File and batch hashes identify the
exact source set. Source data and generated SQL are not committed to Git.

The source snapshot contains 8,433 nonempty source rows across all worksheets:
655 monitoring, 5,212 procurement, 927 allocation/PP and 1,639 payment rows.
These include headings, summaries and helper worksheets; they are NOT counts of
business entities. The subsequent domain import must select the authoritative
sheets and exclude headings/helpers rather than importing every raw row as data.

## Production cutover on 10 November 2026

The existing domain model was reused instead of replacing all tables. The four
files were parsed by the same import services as the website, first against a
restored database and then against production. `RegistryRefreshRunner.java` is
an offline JDK-21 operator tool using the server distribution's existing import
services. It accepts `preflight` (no database connection) and `apply` modes.

`trial-registry-refresh.sh` retains an isolated restored database for inspection.
`apply-registry-refresh.sh` requires a verified fresh backup and trial approval,
stops the application during import, uses the locally installed image (no GHCR
credentials required), and restores the SQL snapshot if import or protected-link
verification fails. `check-registry-refresh.sh` executes the read-only verification
queries against the trial or live database.

Verified live results:

- 125 active tranche-A subprojects, 71 active tranche-B subprojects, 29 parts.
- 106 additional tranche-B records retained as planned rather than deleted.
- 139 procurement records, 181 payment summary/component records, 93 payments.
- Four users and two inspections retain their original identities and links.
- No orphan inspection/contract references and no duplicate project codes.
- The 125 primary summary rows total UAH 304,888,750.26. The cached approved
  dashboard E4 is UAH 304,888,750.30 and remains stored exactly as supplied.
- The 93 dated payments independently sum to UAH 309,119,376.61 (Works:
  307,988,578.28; TS: 1,130,798.33). This differs from the cached E4 source
  aggregate; the import preserves both rather than fabricating reconciliation.
- One contract (`CK08_05_TS$1`, DB6.3 row 126) has text rather than a URL in AC
  and no Excel hyperlink target. Its missing link was not invented.
- The public HTTPS health endpoint returned HTTP 200 after restart.

No application build or database-schema replacement was required for this data
refresh. Further replacement of the existing domain schema is separate work;
the source-owned staging snapshot preserves all supplied columns in the meantime.
