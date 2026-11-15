# Calculation corrections — 15 November 2026

## Implemented in this change

- Project list, map and project details now share one cash projection from the imported payment transaction register. A successfully loaded empty snapshot is authoritative; only an absent snapshot permits legacy fallback.
- Payments retain exact lot attribution and roll up once to each ancestor. Works UAH excludes TS/CSC; total EUR includes all actual payments. Missing EUR conversion is unavailable, not a partial total or invented zero.
- Map financing falls back to the existing registry EIB cost when the optional legacy amount entry is absent. Conflicting budgets are not overwritten.
- Dashboard card/chart drilldowns pass their selected tranche to the target screen.
- Financial transaction drilldowns preserve project, region, tranche, year, month and payment purpose. Cumulative source rows are scoped only by project population, not by transaction period/purpose. A reset action clears the drilldown context.
- Payment table PDF retains the selected project population and labels its count as records (including component rows), not unique subprojects.
- Removed the inactive currency selector beside the explicitly UAH source aggregate; it changed a retired ledger, not that aggregate.

## Verification

`tools/test-calculation-projections.ps1` compiles actual pure Kotlin production sources with cached dependencies and runs 15 JUnit regressions, including exact-lot attribution, empty-source precedence, unavailable conversions, combined transaction scope and PDF selection. This is a focused fallback, **not a substitute for a complete Gradle/frontend build or browser verification**.

The Windows Gradle daemon still fails before task execution with `Unable to establish loopback connection`. Full CI build and deployment status must be recorded separately, not inferred from these focused tests.

## Explicitly not changed yet

No production records/schema were changed by this patch. Percentage import precision/stale blank values (including MY08_02), inconsistent duration formulas, legacy ledger summaries, decimal frontend conversion, missing-conversion completeness and locale sorting remain separate audited corrections. Budget-source conflicts and the 51,290 UAH / 497 EUR source pair require source reconciliation, not guessed replacements.
