# Regional planned financing / actual payments — 16 November 2026

## Business basis

The screenshot is a layout reference, not a source of amounts. Following the user's clarification, the two series are planned financing and actual payments, not a separate subsidy metric.

- Plan: the existing Dashboard overview's approved `subprojectFunding.amountUah` projection. This deliberately reuses its AT/monitoring funding precedence and active-subproject population instead of substituting signed contract values or introducing a new budget formula.
- Actual: the approved payment-monitoring transaction register's original UAH amounts, including Works, TS and CSC. Lots roll up to their parent subproject's oblast. No exchange-rate assumption or legacy-ledger fallback is introduced.
- SP/tranche/oblast selections restrict both series. Year/month/payment-purpose selections restrict cash only; the full approved plan has no matching period grain. This distinction is visible in the chart hint.
- Parent budgets appear once; parts and duplicate project codes cannot inflate the plan. Missing amounts remain unavailable, whereas an available empty cash snapshot is genuine zero. Cent totals use checked addition.

## UI and integration

A separate responsive, collapsible card is placed below Financial Monitoring summary cards and above existing charts. Paired horizontal bars share one UAH scale. The primary OMS color denotes plan and a contrasting brown-orange denotes cash. Every oblast and series has exact cent-preserving labels and a tooltip. Compact existing OMS sort controls sort localized oblast names or planned values, with ascending/descending direction. A bounded internal vertical scroll keeps the page compact. The existing shared responsive header prevents title/toolbar overlap.

An independent asynchronous overview request supplies the authoritative plan without blocking other financial cards. Existing payment/project loading failures show an error state and a retry action using the page's established reload mechanism. UA/EN strings use the existing translation infrastructure. There are no backend/schema/import/data changes.

## Verification

The focused production-source runner passed 82 JUnit checks, including five new regional aggregation tests for lot/service payments, period/purpose scope, tranche aliases, oblast grouping, conservation, duplicates, missing versus empty data and overflow. Existing decimal, monthly, sorting and serialization audits passed. Web regex/cursor policies and whitespace checks passed.

The full frontend Gradle compile was attempted locally but failed before compilation with `Unable to establish loopback connection`. Full Compose compilation, browser rendering and production availability must be verified by CI/deployment separately; no local browser or live visual success is claimed.
