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

## Production percentage repair

On 15 November the deployment of `4797f13` was verified by its exact `/version` revision, healthy API/readiness and all four successful workflows. This is release/API verification, not a new manual browser UI check.

The read-only source was `DB_Monitoring table.xlsx`, sheet `DB Table`, columns AN / CC / CD / CE; SHA-256 `523cafdd8fddf05089a2bffbba3ac2aef8c80dedf6308ad847f91e69e8b0ad91`. The guarded plan changed 19 production monitoring rows: 23 JSON percentage values and 20 matching typed values. In particular, MY08_02's genuinely blank CC106/CD106 cleared the stale `697 192,92` / `9.71%`, while its cached CE106 numeric zero became `0%`. No invented completion percentage was substituted.

Backup: `/docker/oms/backups/registry-20261115T112819Z`, including database and uploaded files. A restore check and an isolated trial preceded the production transaction. Trial checks found zero changes outside the allowed percentage columns/JSON keys. Before/after dumps of every other table and upload checksums matched exactly. The entire repaired monitoring table matched the verified trial.

Post-repair verification compared all 145 source-linked registry rows, confirmed all 23 changed values through the production API and checked MY08_02 details. Regenerating the plan against the post-repair export yielded **zero further repairs**. Four offline repair/verifier tests pass and are included in backend CI.

Formula cells without cached results are deliberately skipped, not treated as proven blanks. The previously populated LV08_04 AN101/CE101 remain unresolved. Broader map/dashboard reconciliation was attempted but the public dashboard response body timed out; no successful end-to-end chart verification is claimed from that attempt.

## Explicitly not changed yet

The production repair changed only the verified percentages described above, not the schema or monetary data. Subsequent duration and decimal-display corrections are documented in CONTRACT_DURATION_CORRECTION_2026_11_15.md and CURRENCY_ROUNDING_CORRECTION_2026_11_15.md; their deployment is tracked separately. Legacy ledger summaries, missing-conversion completeness and locale sorting remain audited follow-ups. Budget-source conflicts and the 51,290 UAH / 497 EUR source pair require source reconciliation, not guessed replacements.
