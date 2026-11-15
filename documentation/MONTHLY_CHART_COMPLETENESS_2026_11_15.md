# Monthly financial chart completeness

## Confirmed failure

The Financial Monitoring purpose charts used `mapNotNull` on currency conversion. One missing rate could silently remove a payment, producing an understated bar/tooltip. If the entire TS/CSC/equipment series was unconvertible, some cards disappeared as if no payments existed. Rate lookup failures were swallowed without chart-level retry, were only requested for EUR-to-UAH, and fetched rates could override newer supplied rates.

## Correction and scope

- Works, TS, CSC and equipment monthly aggregations now report unavailable records and withhold the affected chart's series/tooltip instead of presenting a partial total. Other complete purposes and currencies remain usable. Missing records are not inserted as artificial gap zeros.
- The chart title, currency selector and collapse control remain available. Loading and localized UA/EN error states reuse `ContentState`, including retry. Empty source data still uses the normal no-data state.
- Missing-rate lookup works in both currency directions, respects frozen conversions (including zero), deduplicates dates and preserves valid supplied-rate precedence. Invalid rates do not poison the cache.
- A 20-second batch deadline preserves earlier successful dates. Retrying requests only still-unresolved dates. Navigation cancellation propagates normally and loading terminates in `finally`.
- Invalid effective payment dates and overflowing monthly Long sums are also unavailable, not dropped or wrapped into plausible chart values.

No source amounts, stored conversion rates, import logic, backend endpoint or database row was changed. This correction concerns the purpose-specific transaction charts, not an assertion that every other dashboard/aggregate endpoint has been audited for completeness.

## Verification

The focused runner passed 41 JUnit tests using extracted actual monthly calculation source and the actual coroutine rate loader. New coverage includes partial conversion, missing-month vs real-zero gaps, retry recovery, purpose/currency isolation, cache precedence, bad dates, overflow, network failure, deadline and navigation cancellation. Existing 1,000-input decimal reconciliation still has zero discrepancies. Regex and button-cursor checks pass.

Browser tests for the asynchronous loader and UA/EN message coverage were added to normal CI. Their execution, the full Compose build and deployment are separate from the focused JVM results; no local production-browser success is claimed here.
