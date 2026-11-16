# Financial execution denominator audit — 2026-11-16

## Baseline and reproduction

Release `6dc1ebf159df` is deployed; all four workflows completed successfully.
This pass covers the Works execution base shared by financial progress charts
and individual project PDF cards, not another refresh of source data.

The fallback from a missing parent Works contract to lot-level contracts still
used binary `Double.sum()`, unlike the other recently corrected totals. Two
new strict regressions failed on the actual helper before the fix:

- expected 0.3, actual 0.30000000000000004 for lots 0.1 and 0.2;
- expected 0.009, actual 0.009000000000000001 for lots 0.005 and 0.004.

This is a sub-cent precision defect, not evidence of materially missing money.
`financialProgressSourceWorks` now folds the valid source values with BigDecimal
and converts the total once to the existing API representation. It does not
round individual lots or change parent/lot precedence, payment purpose filters,
scope selection or handling of blank/nonpositive source fields.

## Read-only source provenance

`tools/audit_financial_execution_bases.py` independently examines stored raw
monitoring values with Decimal arithmetic. Across 302 subprojects it finds:

| Execution-base origin | Subprojects |
| --- | ---: |
| Parent Works source amount | 49 |
| Sum of available lot Works source amounts | 5 |
| Stored contract fallback | 5 |
| Approved-budget fallback | 243 |
| No positive base at any level | 0 |

These counts include all project statuses, not only the 196 approved active SPs.
They describe existing fallback provenance; a budget fallback is not being
asserted to be a signed contract. Source amounts and fallback policy were not
rewritten by this pass.

| Lot-based SP | Decimal base, UAH | Populated lot Works amounts / parts |
| --- | ---: | ---: |
| ZH08_04 | 51,415,245.74 | 3 / 4 |
| RV08_04 | 26,145,000 | 2 / 2 |
| KH08_07 | 11,598,406.13 | 4 / 6 |
| KH08_09 | 58,510,717.21 | 2 / 2 |
| KH08_11 | 182,328,272.94 | 2 / 2 |

For ZH08_04, binary summation differs from the source decimal sum by -0.000000005
UAH. The missing `ZH08_04#Lot4` source Works amount has a matching procurement
row with **Contract terminated** status; its TS contract is a different purpose.
`KH08_07#Lot4` and `#Lot5` have no matching procurement rows in this export.
These observations do not justify filling absent Works contracts with approved
budgets, including TS, or claiming a complete contract register. The tool makes
partial source coverage explicit for further owner/source reconciliation.

## Verification and limits

After the fix all 112 focused Kotlin regressions pass, including both previously
failing exact comparisons, parent precedence, child order independence and
preservation of fractional source precision. Existing 1,000 decimal fixtures and
payment completeness/scope checks pass. Three new independent audit tests plus
16 budget/planner Python regressions pass (19 total). CI now includes the new
audit regressions.

No production DB, workbook, PDF layout or frontend changes were made. PDF
generation/rendering and a browser chart interaction were not executed in this
pass; the tested shared calculation is used by their existing code paths. Full
server source compilation passed with the direct Kotlin 2.3 compiler, bypassing
the local Gradle loopback issue; existing unrelated warnings remain. This is not
claimed as a local Gradle build. Release status is reported separately. The broader financial
audit is not marked complete.
