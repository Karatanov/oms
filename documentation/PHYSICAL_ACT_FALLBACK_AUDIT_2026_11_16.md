# Physical ACT fallback / live reconciliation — 16 November 2026

## Verified remaining defect and correction

`dashboardSubprojectProgress` converted an unavailable UAH ACT amount to zero before summation. Where no valid source completion was reported, an incomplete ACT total could therefore become an apparently valid physical-completion percentage.

The fallback now uses the existing decimal, completeness-aware aggregation. A valid reported completion (including zero) retains priority over ACTs. A present but unavailable ACT total is not a missing ACT: the affected subproject has `hasCompleteAmounts=false`, so the existing chart omits only that subproject and identifies its code. No ACTs still means genuine zero. Invalid source completion cannot be silently bypassed with ACT data. The UA/EN notice now covers source completion and ACT amounts/saved rates.

The denominator, contract attribution, source percentage bounds, cash/financial charts, APIs' field shapes, database and source workbooks are unchanged. No speculative source correction was made.

## Read-only production reconciliation

Release `35ec734` passed all workflows and was verified by exact revision `35ec7343d07c`, healthy API and readiness. The fresh registry/dashboard drilldown audit returned:

- 332 project-list rows; 196 active parent SPs = tranche A 125 + tranche B 71.
- Approved funding: all `6,700,750,492.25` UAH; A `4,253,692,449.090001` and B `2,447,058,043.16` as JSON floating-point values. The partition reconciles to a cent tolerance; the sub-micro-unit float tail is not a monetary discrepancy.
- A+B=All checks passed for SP count, approved UAH/EUR funding, signed procurement count and signed value.
- No zero map disbursement despite positive dated/primary source payments was found. Three positive-payment detail checks matched the corresponding list field exactly.
- The audit's 36 absent optional legacy financing entries are **not proof of 36 broken map cards**: the current Leaflet projection already falls back to `registry["eibCost"]`. That diagnostic inspects only the optional amount entry, not the rendered fallback.
- 51 budget/snapshot disagreements remain. No mass replacement was attempted: the source precedence and original values must be reconciled first.

These are API/database/code observations, not a new visual browser check of every card/chart.

## Tests and limits

97 focused JUnit checks passed, including new reported-source precedence, unavailable ACTs, genuine zero/absence and invalid-source/non-finite cases. Existing decimal, monthly, scope, sorting and serialization checks passed. All server production sources compiled successfully with the standalone cached Kotlin compiler; pre-existing warnings remain. Full Gradle/Compose build and CI/deployment are tracked separately. The audit does not close source-budget conflicts, the CK08_06 source currency pair or formula cells without cached results.
