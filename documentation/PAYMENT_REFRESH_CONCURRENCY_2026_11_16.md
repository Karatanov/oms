# Payment refresh availability and cancellation — 2026-11-16

## Verified baseline

Production is `b3e1844b7bba`, all four workflows succeeded. A fresh independent
read-only financial reconciliation passed 49/49 checks. Current totals include
313,077,334.60 UAH dated cash, of which Works is 311,946,536.27 UAH and TS is
1,130,798.33 UAH. No production workbook import or database update was performed.

Cache review did not substantiate the initial suspected stale project-payment
projection: the map/list/details project routes obtain disbursements from the
current imported payment snapshot. Financial-screen successful refresh also
increments its reload key. No speculative cache invalidation was added.

## Reproduced service failures

Deterministic tests use the real `PaymentMonitoringService`, a fake repository,
an injected parser and latches; they do not process customer workbooks or access
the production database. Before the correction two tests failed:

1. Cached snapshot read timed out while synchronous `importWorkbook` was held in
   parsing. The method's `@Synchronized` monitor covered both POI parsing and
   replacement, and `current`/`currentIfAvailable` required that same monitor.
2. An async refresh cancelled while waiting to acquire the snapshot monitor still
   replaced the repository and reported `COMPLETED` rather than `CANCELLED`.
   Its cancellation checkpoint happened before the potentially blocking monitor
   acquisition, with no recheck once the monitor became available.

The first failure covers the synchronous upload/automatic-import path; async
refresh already parsed outside the snapshot monitor. The second covers the
asynchronous refresh path. These are reproduced service defects, not a claim
that a particular historical production outage was caused by them.

## Minimal correction

- `importWorkbook` now parses outside the published-snapshot monitor.
- A separate parser lock serializes parse operations, preserving safety of the
  existing shared formatter without blocking snapshot readers.
- Repository replacement and publishing the cache remain synchronized. A failed
  repository transaction never publishes an uncommitted snapshot.
- Async refresh rechecks cancellation inside that monitor immediately before
  invoking the repository. Cancellation while waiting therefore retains the old
  snapshot. Once repository replacement has started, its atomic transaction is
  allowed to complete; this is not transaction interruption or rollback-on-click.
- The parser constructor argument defaults to the same production parser. It
  exists to control phases deterministically in tests, not to bypass validation
  in an API route.

No source-field interpretation, financial formula, database schema, permissions,
download configuration or frontend behavior was changed.

## Checks and limits

The five concurrency regressions cover readable old snapshot during parse,
cancellation while queued for replacement, parse failure, transaction failure,
and serialization of two parsers independently of snapshot reads. All five pass
after the fix. All 124 focused calculation/client-compatibility tests pass;
existing 1,000 decimal fixtures and 19 Python regression checks also pass.

Full server compilation with the direct Kotlin 2.3 compiler passed, as did two
additional server DTO tests and ten repeated runs of the five concurrency cases
(50 executions). The direct compiler bypasses the local Gradle loopback issue;
this is not claimed as a local Gradle build. Existing compiler warnings and a
standalone test-classpath Log4j provider warning remain unrelated to the fix.
Tests use controlled parser/repository
collaborators; no real browser timing, customer Excel import or production cancel
operation is claimed as tested. Existing short synchronization around repository
reads/replacement is retained, so this does not promise lock-free reads during
the actual database transaction.
