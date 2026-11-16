# Payment date full-string validation — 2026-11-16

## Reproduced failure

`parsePaymentWorkbookDate` parsed `value.trim().take(10)` as an ISO date before
trying the strict US format. Any string beginning with a valid date could
therefore enter the actual-paid transaction path, even with a pending/cancelled
suffix or an invalid timestamp.

Two new regressions failed before the correction:

- `2026-11-03 Planned` returned 2026-11-03 instead of null.
- `2026-11-03T99:00:00` also returned 2026-11-03 instead of null.

This demonstrates an importer validation defect. No claim is made that a
particular customer workbook currently contains these suffix forms, or that the
previously audited 96 production transactions need deletion on this basis.

## Correction

The parser now consumes the entire field. It accepts complete ISO dates,
strict US month/day/year dates and valid ISO timestamps. Existing SQL-style
timestamps with one space between date/time are normalized to ISO `T` and then
validated in full, rather than discarding the time. Outer whitespace is trimmed.
ISO offsets are accepted without converting the source calendar date to UTC.

Textual statuses and malformed/incomplete timestamps return null. The existing
transaction parser consequently excludes them as undated rows. It does not
extract embedded dates or infer payment from a pending note.

Typed Excel-date handling, monetary amounts, source references, batch/type
classification, cumulative indicators and source authority remain unchanged.
No production workbook, database row or frontend code was modified.

## Verification

- Three new pure date-policy tests cover status suffix rejection, valid date/
  timestamp compatibility, invalid time/leap-date handling and source-date
  preservation with offsets.
- Three existing payment-date regressions were added to the focused runner,
  including an in-memory XLSX import fixture. All three actual April-date rows
  remain imported while Planned/Sent-for-payment notes remain excluded.
- All 139 focused Kotlin tests and 1,000 existing decimal fixtures passed.
- All 23 offline Python regressions passed.

Full server source compilation passed using the direct Kotlin 2.3 compiler,
bypassing the local Gradle loopback limitation. Two additional server DTO
serialization tests passed (141 distinct Kotlin tests including the focused
runner). Existing compiler and standalone logging-provider warnings remain.
This is not claimed as a local Gradle build. Release status is reported
separately. The workbook fixture is a software regression test, not a modified
customer spreadsheet or proof of a live production import. Browser interaction
and automatic reimport of the authoritative workbook were not performed.
