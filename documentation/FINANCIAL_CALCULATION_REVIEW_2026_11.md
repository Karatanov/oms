# Financial aggregation review, 10 November 2026

## Confirmed corrections

- Normalize source categories before aggregation, using shared Kotlin keys on
  the server and client. Previously, multiple shelter source descriptions became
  separate aggregates but were subsequently displayed as the same translated name.
- `Security Centres` and `Центри безпеки` now form one security-centres category.
- Per the customer's OMS terminology, `renovation` belongs to reconstruction.
  `Overhaul / Капітальний ремонт` remains capital repair, rather than being mapped
  to an ambiguous `renovation` key with no matching client translation.
- Financial progress prefers the exact source `constructionAmount` denominator.
  The legacy project contract attribute can contain Works + TS + CSC totals and
  therefore must not override an explicit construction-only source amount.
  Positive stored contract amount and approved budget remain fallbacks.

## Formula and reconciliation checks

- Progress is `sum(works paid UAH) / sum(execution base UAH) * 100`, not an
  unweighted average of individual ratios. W payments exclude TS/CSC payments.
- Canonical category merging must conserve both numerator and denominator.
- The source refresh independently reconciled 93 dated payment rows to
  UAH 309,119,376.61. Source E4 remains UAH 304,888,750.30, as supplied; these
  are distinct source controls, not values to force into agreement.
- Primary subproject payment summaries must not be added to their W/TS/CSC
  child component rows a second time. Existing dashboard logic already filters
  primary rows, so that logic was preserved.

## Verification and limits

### Follow-up: lot attribution and percentage units

- `paymentMonitoringSubprojectCode` removed `#Lot` before stripping `_W`/`_TS`/
  `_CSC`. For `KH08_07#Lot1_W`, that produced `KH08` and lost the project match.
  The suffix order is corrected and plain subproject codes are preserved.
  Independent source inspection found 22 dated Works lot payments totalling
  UAH 49,522,160.76 affected by this attribution defect in grouped progress.
  The payments themselves were stored; this is not missing imported money.
- Numeric Excel cells with a percent number format are fractions. The XLSX
  reader now converts only such cells to API percentage points before storage:
  `0.765 -> 76.5`. Plain numeric `76.5` and textual `76.5%` remain 76.5.
  This also preserves a genuine `0.5%` cell instead of applying a value-based
  heuristic that confuses small percentages with fractions.
- Existing stored share values require a payment workbook refresh after the
  corrected backend is deployed. No heuristic rewrite of historical database
  values is performed. Lot attribution takes effect immediately on new reads
  after deployment and requires no reimport.

Standalone Kotlin checks execute the shared canonicalization and verify alias
grouping conserves totals. Backend regression tests cover the weighted ratio,
aliases and construction-only denominator fallback, including fractional UAH.
Local Gradle tests/build remain blocked by `Unable to establish loopback
connection`; CI is required for the complete backend/frontend build.

This is a review of the affected financial aggregations and source controls,
not a certification that every Excel formula or every other OMS calculation
has been audited. Further source-formula and module-wide review remains open.
