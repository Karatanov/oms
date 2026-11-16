# Source payment rollup cutoff — 2026-11-16

## Scope and method

Read-only review of the user-provided local `UR III Payments.xlsx`, SHA-256
`846229736506c963e042cf9c2bd68559b51ebda2a0d0b945ef346d3eb41791ed`.
The spreadsheet skill guided separate inspection of stored formula text and
cached values. Bundled Python/openpyxl read the workbook twice (`data_only`
true/false); it was not saved or recalculated. Before/after file hashes match.
No workbook style, formula, cached value or production database was changed.
No claim is made that the current private Google workbook has identical contents.

## Verified formula chain

On `UR III UAH Payment Dashboard`, F2 is `Amount paid, 2026 UAH`.
Calendar rows 2–3 identify J as February, R as October and S as November 2026.

| Source result | Cached value | Owning formula / evidence |
| --- | ---: | --- |
| Dashboard F318, KH08_09#Lot1_W | 2,831,540 UAH | `SUM(J318:R318)` inside LET |
| Dashboard S318 | 7,254,317.30 UAH | November worksheet SUMIFS |
| November payments D3/E3 | 2026-11-03 / 7,254,317.30 UAH | Actual dated source row |
| Dashboard F410, CH08_03_W | blank | `SUM(J410:R410)` inside LET |
| November payments D4/E4 | 2026-11-03 / 934,267.05 UAH | Actual dated source row |

The annual formula ranges stop at October. Consequently the November source
payments cannot flow into annual F, cumulative E or monitoring G through this
chain, although OMS imports those actual dated payments for monthly cash charts.

For KH08_09, the primary monitoring row G317 is 14,999,961.06 UAH. Its dated
cash total is 22,254,278.36 UAH: the exact difference 7,254,317.30 is the excluded
November payment. This is not evidence that OMS duplicated the payment.
For CH08_03, primary monitoring G409 is blank while the dated November cash
payment exists. The analogous EUR dashboard formulas also stop at October.

The intent behind that reporting cutoff is not established from formula text.
It may be a maintained closed-month summary rather than a forgotten extension;
the inconsistent generic `Amount paid, 2026` label does not settle that policy.
The worksheet owner must confirm the intended cutoff before authoritative
summary amounts or formulas are replaced.

## Why blindly extending SUM is unsafe

The November sheet includes both actual dated payments and textual pending
rows, such as `Sent for payment 09.11.2026`. For CH08_03, S410 is 983,439 UAH,
including the 934,267.05 dated payment and a pending 49,171.95 entry (November
row 16). The monthly SUMIFS does not distinguish those statuses. Expanding
the annual range through S would therefore not reliably mean "actually paid".

Safe source reconciliation requires an explicit reporting cutoff and a paid-
versus-pending criterion, preserving actual dates and amounts. OMS's dated cash
charts should not silently delete valid November payments to match a shorter
source rollup, nor should its source-backed table silently substitute a newly
derived cumulative figure without agreement on that measure.

## Regression prevention

`tools/audit_payment_rollup_cutoffs.py` is an independent read-only source check.
It identifies the annual year and month coverage from actual headers, parses
only the supported single-row SUM range, and reports dated cash rows outside
that coverage. Unsupported formulas are reported, not guessed. Pending strings
are never interpreted as actual payment dates. It preserves Decimal sums and
checks source SHA-256 before and after reading.

The review finds exactly two affected component rows, F318 and F410. Four
offline tests cover range/row validation, missing November coverage, pending and
other-year exclusion, exact decimal sums and resolved warnings after coverage
extension. They do not load customer workbooks or need openpyxl in CI; the
analysis dependency is loaded only for the operator's workbook audit command.

The new tests are added to backend CI. No application runtime change, database
mutation, full Kotlin compilation or browser interaction is part of this pass.
