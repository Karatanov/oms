# OMS Phase 1 / MVP

OMS is a project monitoring system for the TVET II programme. Phase 1 provides authentication and RBAC, dashboard, project registry and map, project details, inspections/SIR, financial monitoring, documents, procurement reference data, and user administration.

## Technology and modules

- `composeApp/` — Compose Multiplatform Web UI, compiled to Kotlin/Wasm (with a JS target retained for development compatibility).
- `server/` — Ktor/JVM API and packaged Web assets; Exposed repositories, Flyway migrations and filesystem upload storage.
- `shared/` — small Kotlin Multiplatform shared module used by JVM, JS and Wasm targets.
- `server/src/main/resources/db/migration/` — ordered MySQL migrations; never edit a migration already applied to a shared database.
- `documentation/` — the authoritative documentation index, manuals, specifications and D9-D12 reports.

There is no Android/iOS application module in this Phase 1 repository. Mobile packaging, signing and distribution are Phase 2 work; shared KMP extension points remain available.

## Prerequisites

- Git;
- JDK 21 (the Gradle build enforces Java 21 bytecode);
- Docker Engine with Docker Compose for the integrated local stack;
- a current Chromium/Firefox/Safari browser with WebAssembly GC support for the Wasm UI.

Use the checked-in Gradle Wrapper. No global Gradle installation is required.

## Quick start with Docker

1. Copy `.env.example` to an untracked `.env`.
2. Replace `MYSQL_ROOT_PASSWORD`, `DATABASE_PASSWORD` and `OMS_JWT_SECRET` with local development values. Never commit `.env`.
3. Build the complete UI/API image and start the stack:

   ```shell
   docker compose up -d --build
   ```

4. Wait until `docker compose ps` reports both services healthy.
5. Verify **GET** `/health` and **GET** `/api/v1/health` on `http://localhost:${OMS_PORT:-8080}` and open the same base URL in a browser.

MySQL data and uploaded files are persisted in the `mysql-data` and `oms-uploads` named volumes. Flyway initializes an empty database automatically. The MVP intentionally uses filesystem-backed upload storage; MinIO/S3 is not required.

## Development processes

For a backend process outside Docker, export the variables from `.env.example`, start only MySQL with `docker compose up -d mysql`, then run:

```shell
./gradlew :server:run
```

On Windows use `./gradlew.bat`. The Web development server commands are:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
./gradlew :composeApp:jsBrowserDevelopmentRun
```

The development UI calls `http://localhost:8080/api/v1`. The integrated production image serves UI and API from one origin.

## Configuration

`.env.example` is the source of truth for local environment names. Required persistent-deployment values are database credentials and a random `OMS_JWT_SECRET` of at least 32 characters. `OMS_UPLOAD_DIR` selects the filesystem root; `OMS_LOG_LEVEL` defaults to `INFO`; `CORS_ALLOWED_HOST` is needed only when the frontend is hosted on a different origin.

No production credentials, TLS keys or production `.env` file belong in Git.

## Build and test

Representative handover commands (Windows: use `gradlew.bat`) are:

```shell
./gradlew :shared:allTests
./gradlew :composeApp:wasmJsTest
./gradlew :composeApp:wasmJsBrowserDistribution
./gradlew :server:test
./gradlew :server:installDist
docker compose build
```

`server:test` contains API/security and SIR workflow coverage. The shared and Web suites are environment-independent. Docker is required for the production-like MySQL/Flyway/startup rehearsal. Browser acceptance checks use the packaged application and the workflows described in the User Manual and D9 report.

## Database, storage and operations

Flyway runs at application startup and applies migrations through V29. Migration V27 deliberately replaces disposable project/inspection/financial/document/non-admin-user data with the TVET II reference hierarchy; review it before upgrading a database that predates V27. Back up MySQL and the upload volume together before production migration.

Health endpoints are unauthenticated. All business endpoints are under `/api/v1`; browser authentication uses an HTTP-only session cookie and non-browser clients may use the returned Bearer JWT. Backend authorization is authoritative.

For deployment, smoke testing, rollback and recovery use the D11 report and Administrator Guide linked from [the documentation index](documentation/README.md).

## Documentation

Start with [documentation/README.md](documentation/README.md). It identifies the authoritative architecture, database/ERD, API, UAT, manuals, deployment and handover artifacts.
