# OMS Phase 1 / MVP Technical Handover

**Release:** 1.0.0, branch `master`  
**Handover status:** PASS WITH LIMITATIONS  
**Prepared:** 20 August 2026

## 1. Handover scope and Phase 1 status

This handover transfers the Phase 1 source code, MySQL migrations, Compose/Render deployment configuration, tests, environment template, user/administrator manuals, technical specification, D9 UAT evidence and D11 deployment material. D0-D10 are implemented and documented; D11 engineering preparation is ready for externally authorised cutover; D12 closes the repository and documentation handover.

## 2. Source code and technology

`composeApp` is the Compose Multiplatform Web/Wasm UI. `server` is the Java 21 Ktor application containing routes, DTOs, domain services, Exposed repositories, Flyway and the packaged Web distribution. `shared` is the KMP JVM/JS/Wasm extension module. MySQL 8 is the primary database. Runtime uploads use approved filesystem-backed MVP storage.

The repository has no Android/iOS application target. Mobile distribution is a Phase 2 extension, not a missing Phase 1 build artifact.

## 3. Architecture and repository navigation

Read `ARCHITECTURE.md` for component/data/security flows, `DATABASE.md` for the ERD and migrations, and `API.md` for HTTP conventions and endpoint groups. The code-level sources of truth are `server/src/main/kotlin/oms/ufsi`, `server/src/main/resources/db/migration`, and `composeApp/src/webMain`.

## 4. Development environment and configuration

Required tools are Git, JDK 21 and the checked-in Gradle Wrapper; Docker Engine/Compose is required for the integrated stack. Copy `.env.example` to untracked `.env`, replace all secret placeholders, and never commit the result. Database credentials and `OMS_JWT_SECRET` are required in persistent deployments. `OMS_UPLOAD_DIR`, pool sizes, `OMS_PUBLIC_BASE_URL`, `OMS_LOG_LEVEL` and optional cross-origin host are documented in the template and Administrator Guide.

## 5. Database and storage

Flyway applies V1-V29 automatically. Empty MySQL initialization and production-packaged startup were verified with a fresh Java 21 distribution. Applied migrations are immutable; V27's intentional operational-data replacement must be understood before upgrading a pre-V27 installation. Filesystem upload metadata is stored in MySQL and bytes in `OMS_UPLOAD_DIR`; database and upload backups form one recovery set. MinIO/S3 is a Phase 2 scalability option.

## 6. Build and test commands

On Windows replace `gradlew` with `gradlew.bat`.

```shell
./gradlew :shared:allTests
./gradlew :composeApp:wasmJsTest
./gradlew :composeApp:wasmJsBrowserDistribution
./gradlew :server:test
./gradlew :server:installDist
docker compose up -d --build
```

The JVM toolchain is pinned to Java 21. Server tests cover application/auth/security and SIR workflow; shared/Web tests cover KMP targets. D9 contains integration/UAT and representative RBAC evidence. Production acceptance uses the integrated Compose stack and the User Manual workflows.

## 7. Running locally and production deployment

For the integrated path, create private `.env`, run `docker compose up -d --build`, wait for both health checks, verify `/health` and `/api/v1/health`, and open the host port in a browser. MySQL and uploads use named volumes. For development-only processes, follow the root README.

The D11 report is the production procedure and go-live checklist. Before cutover: take matching database/upload backups, provide production secrets, TLS/DNS/host access, deploy the immutable release image, verify migrations/health, run critical-role smoke tests, and record approval. Rollback is previous image/config plus restoration of the matching pre-migration data set when schema recovery is required.

## 8. Security and RBAC

Browser sessions are HTTP-only cookies; API clients can use the issued JWT. Passwords are BCrypt hashes and activation tokens are stored as hashes. Runtime logs default to INFO and credential fields from the pool are masked. Protected requests revalidate Active status and role.

Admin is unrestricted. Project Manager is scoped to assigned projects and can manage projects, financials, documents and SIR review. Inspector creates/edits/submits SIR content. Viewer and Guest are read-only. Only Admin manages users. `401` and `403` semantics are documented in `API.md`.

Audit events include user create/update/delete/activation, project create/delete/bulk changes, inspection creation/import, and financial create/import/move. Records carry actor, action, entity, timestamp and optional old/new values; passwords, JWTs and activation tokens are not written as audit payloads.

## 9. CI/CD and operational notes

There is no repository-hosted CI workflow in Phase 1. The verified Gradle commands and documented Docker build are therefore the required release gate until the deployment owner adds CI. `render.yaml` describes the optional demo service; production on-premises deployment follows D11. Logs are stdout/stderr for platform collection. Startup failures expose Flyway/database diagnostics without printing configured passwords.

Troubleshooting order: Compose status and logs, database health/credentials, Flyway version/checksum, `/health`, upload-directory permissions/capacity, then browser network/console. Never use Flyway repair or delete volumes as a generic production fix.

## 10. Documentation inventory

The documentation index links the Supplementary Specification, architecture, database/ERD, API, User Manual, Administrator Guide, D9 UAT report, D11 report and this D12 handover. Source/migrations remain authoritative for implementation detail.

## 11. Known limitations and Phase 2 boundary

- Actual production cutover requires external host, secrets, TLS/DNS and owner approval.
- Filesystem volumes are the approved MVP storage; S3/MinIO integration and enterprise backup automation are not implemented.
- Account activation is queued to local `.eml` outbox; SMTP delivery is not integrated.
- Android/iOS apps, offline sync, notifications, external integrations, advanced analytics and extended compliance/audit features are Phase 2.
- OpenAPI generation and repository CI are not included; maintainers use route/DTO sources and the documented release commands.
- The final clean multi-stage Docker rebuild must be repeated after repairing this workstation's Docker Desktop/WSL data-VHD attach state (`WSL_E_USER_VHD_ALREADY_ATTACHED`). The isolated Java 21 image/startup, empty-database V1-V29 migration, health endpoints and the same Gradle distribution build were already verified; this is a host-platform revalidation item, not a source compilation defect.

## 12. Handover checklist and status

- [x] Source modules, structure and Java target documented.
- [x] Configuration names synchronized; secrets excluded from tracked configuration.
- [x] MySQL migrations and database/ERD documentation included.
- [x] API/authentication/RBAC/SIR/financial/documents/audit behavior documented.
- [x] Build, tests, Docker startup, health and recovery procedures documented.
- [x] User Manual, Administrator Guide, Supplementary Specification, D9 and D11 included.
- [x] Phase 1 versus Phase 2 boundary and limitations recorded.
- [x] Documentation index and official handover artifact included.

**HANDOVER: PASS WITH LIMITATIONS.** The repository is suitable for transfer to a qualified developer/DevOps engineer. Remaining items are explicit external cutover, host-platform Docker revalidation or Phase 2 capabilities, not hidden Phase 1 engineering work.
