# OMS — D9: System Integration Testing & UAT Report

**Date:** 2026-08-20  
**Scope:** D0–D8 MVP  
**Result:** PASS WITH LIMITATIONS

## Environment and approach

Testing used the repository revision containing migration `V29__add_viewer_and_guest_roles.sql`, Gradle/JDK 21 and Docker Desktop with an isolated MySQL 8.4 database. The API UAT environment was built from the already verified OMS distribution image; Flyway applied its packaged migrations to an empty database, and the new D9 migration was validated separately as idempotent MySQL SQL. HTTP checks used real session login and Bearer JWTs.

The test approach combined server unit tests, Web/Wasm compilation, shared-module tests, static review, and API-to-service-to-MySQL UAT. Temporary containers, users, tokens and projects were isolated from the developer database.

## D0–D8 test matrix

| Area | Coverage | Result |
|---|---|---|
| D0 platform | Gradle server build, Web/Wasm compilation, packaged Docker distribution, health endpoint | PASS |
| D1 authentication/RBAC | valid and invalid login, invalid JWT, 401/403, Admin/PM/Inspector/Viewer/Guest, PM foreign-project IDOR | PASS |
| D2 dashboard | authenticated dashboard API for Viewer; project-scoped data path reviewed | PASS |
| D3 registry/map | pagination validation, filtered project API, PM visibility restriction, map route review | PASS |
| D4 details | project, reports, financials, documents and HSE API paths reviewed; unauthorized project rejected | PASS |
| D5 SIR | draft/submit/approve/reject service transitions; backend role guards and completed lock reviewed | PASS |
| D6 financials | role guard, project access guard, import/validation/export service paths reviewed | PASS |
| D7 documents | project association, type/magic/size validation and authorized download path reviewed | PASS |
| D8 administration | pending creation, local activation token, activation, repeat-token rejection, active login, audit-safe fields reviewed | PASS |

## Executed UAT scenarios

1. **Admin:** login; receive JWT; list users; create pending Viewer and Guest; create a project; administrative-only operations accepted.
2. **Project Manager:** login; user administration and bulk reassignment return **403**; direct GET of an Admin-owned project returns **403**.
3. **Inspector:** login; direct financial API request returns **403**.
4. **Viewer:** pending account activated using the issued one-time token; login and Dashboard return **200**; project mutation returns **403**.
5. **Guest:** pending account activated and logged in; permitted project list returns **200**.
6. **Security:** malformed Bearer JWT and invalid credentials return **401**; repeated activation token returns **400**.

## Defects found and corrected

| ID | Finding | Resolution |
|---|---|---|
| D9-01 | Required `VIEWER` and `GUEST` roles were accepted by API guards but absent from the migration schema. | Added idempotent Flyway migration V29, preserving legacy `CONTRACTOR`. |
| D9-02 | Password validation required 8 characters but reported 5. | Corrected the validation message. |
| D9-03 | Root smoke test expected a retired plain-text response after Web assets became the root resource. | Test now asserts the deployed application shell. |
| D9-04 | JWT tamper test could alter unused Base64URL padding bits and still represent identical bytes. | Test now modifies the signed payload. |
| D9-05 | Unused Web UI mock/placeholder remnants were misleading in final review. | Removed the unused placeholder and documented session marker semantics. |

## Regression results

- `:server:test` — PASS (5 tests).
- `:composeApp:compileKotlinWasmJs` — PASS.
- `:shared:allTests` — PASS (JVM, JS and Wasm test reports have zero failures).
- `git diff --check` — PASS.
- Docker distribution image and isolated MySQL startup — PASS; `/api/v1/health` returned `{"status":"UP"}`.

## Known limitations / blocked checks

- D8 sends activation messages to the documented local `.eml` outbox; no external SMTP provider credentials were supplied, so actual external delivery is **NOT APPLICABLE** to this MVP test.
- Object storage remains filesystem storage for the MVP by the approved decision; MinIO/S3 interoperability is **NOT APPLICABLE**.
- Browser-interaction UAT (Leaflet visual clustering/colours and native file-picker UX) was not automated here; the client compiles and the API/data paths passed, but visual browser acceptance remains a manual deployment check.
- The fresh Docker image rebuild was still in progress during the API UAT because its isolated Gradle cache performed a first-time dependency resolution. The existing verified distribution image was used for the full API UAT; V29 SQL was separately validated.

## Final D9 status

**PASS WITH LIMITATIONS.** All five required roles now exist in the deployable schema, critical authentication/RBAC/account-lifecycle and project-isolation paths were exercised end-to-end, and discovered in-scope defects were fixed and retested.
