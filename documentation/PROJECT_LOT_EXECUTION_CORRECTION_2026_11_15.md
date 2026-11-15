# Individual project/lot financial execution — 15 November 2026

## Confirmed defects

`projectFinancialExecution` removed `#Lot…` before creating the payment lookup. An individual lot's PDF therefore used the same parent-level lookup as the entire subproject and could include sibling payments. The legacy fallback also discarded unavailable conversions and summed Long cents without overflow protection, potentially displaying a partial or wrapped execution amount.

## Correction

- Approved payments now use the existing `projectDisbursements` exact-lot/ancestor projection rather than a second parent-only lookup. A parent receives its own and its lots' Works payments once; an individual lot receives only its exact payments. TS/CSC and unrelated subprojects do not affect Works execution.
- Parent-only payments are not guessed or allocated to individual lots. A lot with no matching payments remains a genuine zero.
- A loaded empty approved snapshot stays authoritative and never revives the legacy ledger.
- Legacy Works payments/advances use frozen conversion, require every conversion to be available, and sum cents in decimal arithmetic without Long wrapping. Missing conversion yields unavailable, not a partial percentage.
- The PDF displays a UA/EN explanation for unavailable execution. Known execution, source denominator precedence and over-100% source anomalies keep their existing behavior.

No database records, source workbooks, schema, import behavior or API response fields were changed. This is the financial calculation used by individual PDF cards; other card sections and other aggregate endpoints were not redesigned.

## Verification

- Focused standalone suite: 70 JUnit tests passed, including exact-lot selection, parent/chart reconciliation, contract-row suffixes, empty source precedence, missing rates, decimal rounding and large aggregate amounts.
- Full production server sources and PDF tests compiled with the cached Kotlin compiler; pre-existing warnings remain. This is a fallback, not a full Gradle build.
- Eight PDF regressions passed, including new UA/EN lot amounts and unavailable-state checks, plus existing duration, title/link wrapping, photos/attachments and over-100% execution checks.
- Four synthetic UA/EN previews (known and unavailable execution) were generated with the actual exporter, rendered with Poppler and visually inspected. The corrected lot amount and explanatory text fit within their table cells. No production document or photo was used.
- Web regex/cursor policies and whitespace checks passed. CI/deployment and live browser verification remain separate release checks, not implied by local PDF tests.
