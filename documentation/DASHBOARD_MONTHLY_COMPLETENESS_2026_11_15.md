# Dashboard monthly currency completeness — 15 November 2026

## Verified defects

- `MonthlyAmountsChart` substituted `0L` for missing UAH and ignored `hasCompleteEurAmounts`.
- The active dashboard `MonthlyActPaymentsChart` discarded incomplete months with `mapNotNull`, displaying an apparently continuous/comparable series with months missing.
- `dashboardMonthlyPayments` used unchecked Long sums, which can wrap at the numeric boundary while keeping the currency marked complete.

## Fix

Both frontend components use one pure `projectMonthlyAmounts` projection. A currency series is rendered only when all its monthly amounts are available. Otherwise it has no bars or PNG export and shows a localized UA/EN warning with the number of affected monthly aggregates. The independent complete currency is still usable. Genuine zero months, negative corrections, chronology and source immutability are preserved.

The dashboard warning includes a retry action wired to its existing reload mechanism; it retains the selected tranche. No new request, rate lookup, dependency or data mutation was introduced.

Backend addition checks overflow in each currency separately. UAH becomes null when incomplete; EUR retains its existing non-null wire field with an unavailable flag, preserving the response shape for cached clients. The EUR placeholder is never rendered by the updated components.

## Verification and limits

`tools/test-calculation-projections.ps1`: 63 JUnit checks passed (48 calculations, 4 serialization, 11 monthly backend/projection checks), plus the existing 1,000-input decimal audit and monthly/sorting checks. Web regex/cursor policies and `git diff --check` passed. Frontend projection tests run against the exact production DTO declaration, not a hand-copied model.

The local full Compose JS command was attempted but Gradle failed before compilation with `Unable to establish loopback connection`. CI must verify the complete frontend build and browser tests; no successful local Compose build or production UI check is claimed. Database/workbook values and payment import logic are unchanged. This patch does not resolve separate legacy-ledger conversion policy or conflicting source budgets.
