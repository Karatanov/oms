# Legacy financial summary — 15 November 2026

## Verified defect and boundary

`FinancialRecordService.summary` added raw EUR and UAH amounts, counted all ACT purposes against a construction denominator, and used overlapping invoices/acts/payments as financial execution. This is the legacy ledger, **not** the approved imported UR III payment register.

## Correction

- Both project detail and project-financial endpoints accept `summary_version=2`; the updated frontend requests it explicitly. Requests without it retain V1 semantics for cached/older clients.
- V2 converts once to UAH with existing frozen amounts/rates and decimal-cent rounding. No current NBU request or database write occurs.
- Works ACTs determine performed work; Works PAYMENT/ADVANCE records determine cash execution. TS/CSC do not inflate construction execution.
- The all-document sum remains an explicitly identified inventory statistic, never the cash percentage numerator.
- A category with unavailable conversion returns null and an unavailable-record count, not a partial total or false zero. Other categories remain independently usable. A zero denominator has no percentage.
- The denominator retains the existing construction-contract / planned-budget precedence. This patch does not resolve conflicting source budgets.
- Response metadata identifies version, UAH currency and `legacy_financial_records` source.
- The frontend handles the server's `explicitNulls=false`: missing V2 cash execution must not inherit the ACT percentage. V1 fallback semantics remain compatible.

## Verification

The focused standalone runner executes the actual production calculation and DTO declarations with the Kotlin serialization compiler plugin. Regressions cover mixed currencies, independent categories, unavailable rates, zero denominators, decimal rounding, immutable source records, V1 compatibility and omitted V2 null fields. These checks do not constitute a complete Gradle/Compose build or production browser verification. CI and deployment are tracked separately.

No source workbook, production record or schema is changed. V1 intentionally remains available; other aggregate endpoints and source-budget reconciliation are separate follow-ups.
