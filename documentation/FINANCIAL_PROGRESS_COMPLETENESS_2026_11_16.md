# Financial execution aggregate completeness — 16 November 2026

## Confirmed calculation defect

The `/financials/progress` legacy fallback discarded payments when their frozen UAH conversion was unavailable. It then summed only the remaining payments. A later `map[id] ?: 0.0` also made absent payments indistinguishable from an unavailable SP total. Thus oblast/sector/construction-type groups could show a partial amount and understated percentage as complete. This is a fallback-path defect; production currently has an authoritative approved payment snapshot, so it is not claimed to explain every historical zero.

## Correction

- A payment/SP total retains a present-but-unavailable value instead of disappearing from the map. No matching payment still means genuine zero in the selected scope.
- Grouping propagates unavailable values only to their containing group. Unrelated groups remain visible and unchanged.
- Imported Works payments use the same checked decimal-cent conversion and exact decimal addition. Approved empty snapshots remain authoritative; no current rate or legacy payment is substituted.
- The group denominator and numerator are added in decimal arithmetic. Long wrapping and binary summation drift are avoided. The existing denominator precedence, ratio formula, population, year and Works-only policy are not changed.
- The API adds `hasCompleteAmounts` with default true, retaining numeric fields for cached-client compatibility. Updated clients show an unavailable marker and UA/EN explanation, not a zero/progress bar, for flagged groups. Old responses still decode as complete. Older cached clients do not understand the new flag and require the frontend update.

No source workbook, production data, schema, financial import or permissions were changed. Source-budget disagreements, source exchange-pair anomalies and the separate zero-denominator policy are not resolved by this patch.

## Verification

94 focused JUnit checks passed, including 11 financial-progress calculation/compatibility tests. Fixtures cover unknown conversions, independently complete groups, genuine zero, source precedence, lot/year/purpose scope, invalid imported amounts, exact decimal sums, refunds and aggregate values beyond Long cents. Existing 1,000-input rounding, monthly/sorting audits, regex/cursor checks and whitespace checks passed.

All server production sources compiled successfully using the existing standalone cached Kotlin compiler fallback; pre-existing warnings remain. Full Gradle/Compose build, CI/deployment and production-browser behavior are recorded separately; focused arithmetic tests are not proof of a deployed UI.
