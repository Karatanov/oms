# Calculation corrections — 15 November 2026

## Implemented in this change

- Project list, map and project details now share one cash projection from the imported payment transaction register. A successfully loaded empty snapshot is authoritative; only an absent snapshot permits legacy fallback.
- Payments retain exact lot attribution and roll up once to each ancestor. Works UAH excludes TS/CSC; total EUR includes all actual payments. Missing EUR conversion is unavailable, not a partial total or invented zero.
- Map financing falls back to the existing registry EIB cost when the optional legacy amount entry is absent. Conflicting budgets are not overwritten.
- Dashboard card/chart drilldowns pass their selected tranche to the target screen.
- Financial transaction drilldowns preserve project, region, tranche, year, month and payment purpose. Cumulative source rows are scoped only by project population, not by transaction period/purpose. A reset action clears the drilldown context.
- Payment table PDF retains the selected project population and labels its count as records (including component rows), not unique subprojects.
- Removed the inactive currency selector beside the explicitly UAH source aggregate; it changed a retired ledger, not that aggregate.
- Monitoring percentage import now preserves numeric/cached formula precision with explicit percentage units, including values below 1%. Full DB Table snapshots clear blank source-owned fields; narrower Allocation supplements continue preserving unpublished fields.

## Verification

`tools/test-calculation-projections.ps1` compiles actual pure Kotlin production sources with cached dependencies and runs 15 JUnit regressions, including exact-lot attribution, empty-source precedence, unavailable conversions, combined transaction scope and PDF selection. This is a focused fallback, **not a substitute for a complete Gradle/frontend build or browser verification**.

The Windows Gradle daemon still fails before task execution with `Unable to establish loopback connection`. Full CI build and deployment status must be recorded separately, not inferred from these focused tests.

The first package (`6fb6418`) subsequently passed both the Backend regression tests and Project form regression tests workflows, including the real Compose navigation tests. `MonitoringRegistryAmountTest` also passed all 8 tests locally via the standalone Kotlin compiler against the actual importer (numeric/formula percentages, text values, zero, money precision, locale independence and snapshot precedence). Production deployment and current database contents are separate from these results.

## Explicitly not changed yet

No production records/schema were changed by these patches. Existing stale percentage data (including MY08_02) still requires a guarded source repair or a refresh after the corrected importer is deployed. Inconsistent duration formulas, legacy ledger summaries, decimal frontend conversion, missing-conversion completeness and locale sorting remain separate audited corrections. Budget-source conflicts and the 51,290 UAH / 497 EUR source pair require source reconciliation, not guessed replacements.
