# Performance and reliability audit — 2026-09-23

## Photo thumbnail follow-up

CI run `35921819912`, commit `ddb0d7c`: all 21 backend tests passed,
with no skipped tests or failures, including the disposable MySQL tests.
Thumbnail generation now requests reader-level subsampling rather than decoding
the full raster before shrinking it. Previews fit within 320 x 320 pixels,
never upscale small sources, and retain at least one pixel on each axis.
Original image bytes are not modified by this thumbnail helper.

Synthetic 4000 x 3000 JPEG, three warm-ups and 20 measured samples in CI:

| Thumbnail generation | Previous full decode | Subsampled decode |
| --- | ---: | ---: |
| Mean ms | 81.759 | 54.597 |
| p50 ms | 81.259 | 54.498 |
| p95 ms | 89.082 | 55.144 |
| Maximum ms | 90.984 | 55.449 |

Mean generation time fell by approximately 33% in this synthetic test, not
an end-to-end upload or production benchmark. Artifact:
`backend-test-results/reports/performance/photo-thumbnail.txt`.
Tests cover JPEG/PNG, portrait/landscape, 10000 x 1 and 1 x 10000 images,
small images, invalid input, unchanged source bytes and 50 repeated decodes.
Heap usage was not measured; the existing EXIF normalization path still fully
decodes rotated images and remains a separate profiling target.
No Render deployment was triggered.

## MySQL integration and hierarchy follow-up

CI run `35915898210`, commit `a97b806`: all 17 backend tests passed, with no
skips. A disposable MySQL 8.4 service contains 20,001 findings (20,000 belonging
to another report). No application database credentials or data are used.

After three warm-ups, 30 sequential measured runs produced:

| Scoped findings read | Before (JDBC SELECT-all and filter) | After (actual Exposed repository) |
| --- | ---: | ---: |
| Mean ms | 16.568 | 5.386 |
| p50 ms | 14.741 | 5.217 |
| p95 ms | 25.681 | 6.937 |
| p99 ms | 44.479 | 7.429 |

This compares query shapes using a JDBC baseline and the actual optimized
repository, including its transaction/mapping overhead. It is not an exact
old-build versus new-build API comparison. With only 30 samples, p99 is the
maximum observation; production tail-latency/throughput claims are unwarranted.
EXPLAIN chooses `inspection_report_id` and estimates one row. The test asserts
matching counts and cross-report UUID isolation. The timing artifact is
`backend-test-results/reports/performance/mysql-scoped-reads.txt` in that run.

Twenty-four replacements on eight worker threads left exactly one complete
six-item automatic findings set; the manual finding and 20,000 unrelated rows
survived. Invalid input left the previous set unchanged; empty input cleared
only the automatic category. This validates repository replacement concurrency,
not concurrent workbook and database saves as a whole.

Project subtree traversal previously executed a SELECT-all per node. It now
loads only ID/UUID/parent keys once and builds a child-first order in memory.
Traversal is iterative and rejects cycles before any deletion. Tests cover
unrelated branches, child-before-parent order, cycles, and a 20,000-node chain.
Foreign-key deletion integration and concurrent hierarchy edits remain pending.

Status: initial source audit and query-shape benchmark completed; end-to-end
load/stress audit remains incomplete. No production load or production data
mutations were performed. The supplied task file ends mid-pagination section.

## Follow-up batch

Both previous optimization commits compiled successfully in the GHCR workflow;
Pages also succeeded. A separate backend-test workflow now runs the full JVM
test task on Linux, avoiding the local loopback failure. The stale root test
now verifies the intended redirect to Pages without following external URLs.

Automatic HSE replacement previously used one read transaction plus a separate
transaction for every old deletion and new insertion. It now validates all
entries first, locks the parent report, deletes only the automatic category and
batch-inserts replacements in one transaction. This avoids partially committed
sets and serializes competing replacements. Unit tests cover validation before
writes and empty replacement. Actual database concurrency testing is pending;
the workbook save itself is still outside this database transaction.

Workbook inspection exposed the cause of repeated missing Progress headings:
quality formatting always cleared five rows, including later section titles in
compacted reports. Materials removal similarly always deleted nine rows. Both
now use actual next-section boundaries. POI regression tests cover compact and
full sections, repeated formatting, preserved merged headings and comments.

Validation: the full `:server:test` task passed on Linux for commit `31e95f3`
in GitHub Actions run `35914580148` (job `107362703507`). This includes the new
HSE validation tests and POI section-boundary regressions. The initial test
compilation failure caused by heterogeneous assertion types was corrected and
the complete task rerun successfully. This is not a database integration or
production load-test result.

## Architecture and measurement matrix

The browser client is Compose/Kotlin JS, published through GitHub Pages. The
Ktor/Netty JVM backend uses Exposed JDBC, HikariCP and MySQL-compatible storage;
Apache POI handles SIR workbooks. Files use a filesystem copy and database blob
mirror, which restores files after ephemeral-host restarts. Docker builds the
backend independently of the browser; GHCR and Pages have separate workflows.

| Area | Workload / correctness requirement | Current evidence |
| --- | --- | --- |
| Scoped SQL reads | 100k rows, 1k owners; list and UUID lookup, cross-owner isolation | Source audit; SQLite proxy comparison |
| Dashboard | Overview by role and tranche; consistent aggregates | Duplicate financial-table query removed; runtime pending |
| API latency / throughput | Warm/cold, concurrency 1/4/16/32, p50/p95/p99, HTTP failures | Local read-only harness added; API unavailable |
| Authentication / RBAC | Login/session/logout, 401/403, manager scope | No authorization changes; integration run pending |
| Memory / CPU / soak | Repeated preview/import, bounded concurrency, retained heap | Workbook caches are count-bounded at 24 entries each; profiling pending |
| XLSX / files | Concurrent preview/save/download, restart restoration, upload limits | Size-limited multipart reads exist; race and heap tests pending |
| Frontend | Large registries, sort/filter/page, navigation during requests | Browser performance and correctness run pending |
| Database indexes | EXPLAIN on actual MySQL/TiDB and row estimates | Existing photo/document owner indexes identified; no speculative indexes added |
| Failure behavior | API unavailable, timeout, non-2xx response | Harness counts transport and HTTP errors; application fault injection pending |
| Shared/mobile | Compile and regression; platform-specific coverage | Not yet executed |

## Implemented query changes

Nine scoped reads previously executed SELECT-all and Kotlin filtering:
findings (list/UUID), photos (list/UUID), documents (list/UUID), financial records
(list/UUID), and SIR file lookup. They now place the owner constraint in SQL;
single-item queries also constrain UUID where applicable and use LIMIT 1.
The owner constraint remains combined with UUID, preserving ownership checks.

The second batch moves seven more reads into SQL: programme details, monitoring
details (single and batch), authentication state, failed-login state lookup,
and procurement create/update readback. String username/token matching is
deliberately not altered without checking database collation semantics.

File restoration previously wrote directly to the public local path. A second
request could see that path before writing completed and attempt to parse a
partial workbook/image. Restoration now writes a sibling temporary file and
publishes it with an atomic replace, falling back to a regular move only when
the filesystem does not support atomic moves. Concurrent delete/save/restore
ordering still needs integration stress testing; this does not claim to solve
all file lifecycle races.

Dashboard overview previously loaded the whole financial table twice. It now
fetches it once for the already-authorized project IDs and derives both record
groups from the same result. There are no added caches or changed calculations.

## Reproducible query-shape evidence

Run `python tools/benchmark-scoped-queries.py`. This is an in-memory SQLite
proxy, not an Exposed/MySQL integration benchmark or a Render latency claim.
100,000 synthetic records, 1,000 owners, 40 measured runs after warm-up:

| Metric | SELECT-all + client filtering | Indexed WHERE |
| --- | ---: | ---: |
| Mean ms | 111.292 | 0.135 |
| p50 ms | 110.797 | 0.128 |
| p95 ms | 115.211 | 0.165 |
| p99 ms | 118.191 | 0.174 |
| Rows delivered to client | 100,000 | 100 |
| Matching rows | 100 | 100 |

The script asserts identical records and rejects a UUID scoped to another
owner. EXPLAIN reports an indexed owner search. The defensible application
improvement is reduced rows transferred/materialized; exact production latency
and query plans have not been measured.

## Environment and outstanding work

`gradlew :server:test` and `:server:compileKotlin` failed before compilation:
`java.io.IOException: Unable to establish loopback connection`. This persisted
outside the sandbox and with explicit JBR 21 / IPv4 configuration.

Docker CLI was found outside the sandbox at
`C:\Users\Karatanov\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe`.
Docker Desktop was started. CLI info/version calls did not return during checks;
engine readiness is unconfirmed. This is not a missing-PATH conclusion.
Four local `/health` requests failed at transport level; there was no running
API on localhost:8080. These timings are not application latency measurements.

Use `node tools/load-local-api.mjs http://127.0.0.1:8080 /health 100 4`
against an isolated test instance. Authenticated scenarios accept a test-session
cookie through OMS_LOAD_COOKIE; credentials are never logged. The harness only
accepts localhost, does not follow redirects, and has a 15-second timeout.

Next required checks: compile and repository integration tests with actual
MySQL/TiDB; before/after representative API load; concurrent SIR updates and
file restoration; atomic HSE synchronization; browser navigation and rendering;
long-running heap/CPU profiles; remaining project/user repository client-side
filtering; SQL aggregates and server pagination for unbounded registries.
Do not treat this first batch as a completed comprehensive audit.
