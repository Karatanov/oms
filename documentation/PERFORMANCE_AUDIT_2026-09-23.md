# Performance and reliability audit — 2026-09-23

Status: initial source audit and query-shape benchmark completed; end-to-end
load/stress audit remains incomplete. No production load or production data
mutations were performed. The supplied task file ends mid-pagination section.

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
