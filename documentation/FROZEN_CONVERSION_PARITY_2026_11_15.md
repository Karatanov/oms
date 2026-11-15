# Frozen financial conversion parity — 15 November 2026

## Confirmed mismatch

The frontend's `ApiFinancialRecord.displayAmountCents` could reconstruct EUR from a UAH record's saved rate when saved EUR cents were absent. The server's `financialDisplayCents` returned null instead. A characterization test running both actual functions failed before the fix (`UAH -> EUR, amount=0.0, rate=1.0, saved=null expected 0 but was null`). A positive example is 100 UAH at saved rate 48.125: 208 euro cents on the frontend versus unavailable on the server.

Dashboard ledger fallback had two additional discrepancies: EUR records without cached EUR cents did not use their own original EUR amount, and EUR-to-UAH accepted the legacy rate-1 sentinel as a genuine conversion.

## Correction

- `frozenFinancialCents` accepts database BigDecimal values directly and normalizes supported target currencies.
- Original same-currency amounts remain authoritative. For cross-currency EUR display, an explicitly saved EUR value (including genuine zero) wins over any reconstructed value.
- Without cached EUR, UAH-to-EUR may use only that record's valid saved rate. There are no live NBU requests.
- EUR-to-UAH rejects the legacy rate-1 sentinel and missing/non-positive rates. The same-currency UAH amount and its legitimate saved rate of 1 are not rejected.
- Multiply/divide uses decimal arithmetic and HALF_UP cent rounding; overflow remains unavailable.
- Dashboard monthly ledger fallback and server sorting, project disbursements, normalized summaries and financial-table PDF use this same frozen-value policy through the existing helper.

The approved payment register/import precedence, schemas, permissions and source values are unchanged. This does not make other legacy aggregate endpoints completeness-aware; those remain separate follow-ups.

## Evidence and checks

Read-only production query: 63 legacy ledger records, one EUR payment/advance with rate=1, zero EUR payments missing saved EUR cents, zero UAH records missing saved EUR while retaining a positive rate. No identifiers, credentials or document text were exported; no rows were updated.

The focused runner passed 66 JUnit checks, including a 360-case actual client/server parity matrix, database-decimal precision, saved-zero precedence, sentinel rates, positive/negative amounts and overflow. The existing 1,000-input decimal audit and monthly/sorting checks passed, as did regex/cursor policies and whitespace checks.

All server production sources compiled successfully with the cached Kotlin compiler (pre-existing warnings remain), using the standalone fallback because the local Gradle daemon cannot establish its loopback connection. This is not a claim of a successful complete Gradle/Compose build, production deployment or browser verification; CI/release status must be checked independently.
