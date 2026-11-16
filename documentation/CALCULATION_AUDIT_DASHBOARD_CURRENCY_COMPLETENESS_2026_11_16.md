# Dashboard currency completeness — 2026-11-16

## Verified scope

Release `2ce4f5ed7d69` is deployed and all four workflows succeeded. This pass
covers national-summary legacy-ledger `servicesPaid*` fields, not the imported
UR III cash totals or the E4 control figure. The current UI does not consume the
legacy EUR field as its main imported-payment card; no visible source value is
claimed incorrect merely because this API edge case exists.

The aggregate-only read-only diagnostic
`deploy/vps/export-dashboard-currency-coverage.sh` returned:

| Currency | Payment/advance records | Missing saved EUR | Missing/nonpositive rate | Rate = 1 |
| --- | ---: | ---: | ---: | ---: |
| UAH | 58 | 0 | 0 | 0 |
| EUR | 1 | 0 | 0 | 1 |

The saved EUR amounts sum to 498,464,925 cents. Current EUR coverage is complete.
The native EUR record's rate=1 is not evidence of a valid UAH conversion; the
existing frozen-value policy correctly keeps that UAH equivalent unavailable.
No replacement rate or production data changes are justified by this audit.

## Verified code defects and correction

The national EUR total previously added `amount_eur_cents ?: 0L`. An absent
equivalent therefore silently became zero and could expose a partial subtotal
as complete. Both EUR and UAH sums used unchecked Long arithmetic. Native EUR
without a separately saved EUR equivalent was also omitted, despite the original
amount itself being sufficient for EUR.

- Both currencies now use the same frozen conversion policy as the monthly
  dashboard, including native-currency priority and missing-rate handling.
- `DashboardPaymentTotals.kt` preserves completeness independently per currency.
  Missing values and final Long overflow return null, not a wrapped or partial
  total. Decimal accumulation before range checking also preserves negative
  corrections independently of input order.
- The existing non-null EUR wire field is retained for cached-client compatibility.
  If unavailable its placeholder is zero and the new authoritative
  `hasCompleteServicesPaidEurAmounts` flag is false. UAH stays nullable.
- The flag is propagated through the service, route, server DTO and actual client
  DTO. Its default true preserves older payload decoding; false is serialized
  explicitly even when default properties are omitted.
- The stale source comment claiming multiplication by factual EIB share was
  corrected; primary paid amounts are not multiplied by that ratio.

No database, import, FX source, authentication, UI layout or report changes were
made. The non-null compatibility field alone is not sufficient for a new consumer
to infer EUR completeness; consumers must honor the new flag, just as monthly
chart consumers honor their existing completeness metadata.

## Verification

Five new pure-service tests cover missing EUR, independently known UAH, native
EUR with sentinel rate, genuine zero/empty scope, final overflow/underflow and
negative correction ordering. Server/client serialization regressions cover
old payloads and explicitly false completeness. Client fallback compilation
extracts the exact real DTO declaration, not a parallel handwritten model.

119 focused Kotlin calculation/client-compatibility tests and two additional
server DTO serialization tests passed, as did 19 Python regressions and the
existing 1,000 decimal reconciliation fixtures. Full server compilation with
the direct Kotlin 2.3 compiler passed; unrelated pre-existing warnings remain.
This bypasses the local Gradle loopback limitation, not a claimed local Gradle
or full JS/WASM build. Commit and release status are recorded in the task
handoff. Browser interaction and a fresh deployed response are not claimed until
actually checked. The wider formula audit remains ongoing.
