# OMS MVP

## Temporary Render demo deployment

The demo deployment is a single Docker-based Render **Web Service**. The Ktor
server serves both the compiled Compose Web/Wasm UI and `/api/v1`, so the browser
uses one HTTPS origin and does not need a public API URL or CORS configuration.

```
Browser -> https://<service>.onrender.com (Ktor: UI + API) -> external MySQL 8
```

The application currently uses MySQL and Flyway migrations. Render provides
managed PostgreSQL, not managed MySQL, so do **not** create a Render Postgres
instance for this codebase. For the lowest-change demo, use **TiDB Cloud Starter**:
it is MySQL-protocol compatible, has a free tier, and is supported by the bundled
MySQL JDBC driver and Flyway TiDB plugin. Running MySQL itself in a second Render
service requires a paid persistent disk and is not suitable for Render's free tier.

1. Push this repository to GitHub (the repository root is the directory that
   contains `render.yaml`). In Render, click **New > Blueprint**, select the
   repository and create the `oms-demo` service from `render.yaml`. Alternatively
   create **New > Web Service**, select the same repository, choose **Docker**,
   and use `Dockerfile` at the repository root.
2. Choose **Free** for a temporary demo (expect a cold start after 15 idle
   minutes). Set health check path to `/health`. Render supplies `PORT`; do not
   create it manually.
3. Create a free **TiDB Cloud Starter** instance. In its **Connect** dialog,
   select **Public** and **General**, then generate a password and copy the host,
   port, and prefixed user name. In Render's **Environment** settings add the
   following secrets: `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`.
   Use JDBC syntax, for example
   `jdbc:mysql://<host>:4000/test?useSSL=true&requireSSL=true`.
4. Deploy. On first startup Flyway automatically creates the schema and applies
   the demo/reference seed migrations. Open `https://<service>.onrender.com`,
   then check `https://<service>.onrender.com/health` and sign in using the
   documented demo account.

Uploads use the container filesystem (`uploads/`) and survive only while that
Render instance remains alive. For a temporary demo, avoid treating uploaded
documents/photos as durable data. A paid web service can mount a disk and set
`OMS_UPLOAD_DIR` to a directory under that mount; a proper object-storage
integration is a later, separate change.

| Variable | Service | Secret? | Example |
| --- | --- | --- | --- |
| `DATABASE_URL` | `oms-demo` | Yes | `jdbc:mysql://db.example.net:3306/oms_demo?useSSL=true` |
| `DATABASE_USER` | `oms-demo` | Yes | `oms_demo_user` |
| `DATABASE_PASSWORD` | `oms-demo` | Yes | `replace-with-a-generated-password` |
| `DATABASE_POOL_MAX_SIZE` | `oms-demo` | No | `5` |
| `DATABASE_POOL_MIN_IDLE` | `oms-demo` | No | `1` |
| `CORS_ALLOWED_HOST` | `oms-demo` | No | `oms-frontend.onrender.com` |
| `OMS_UPLOAD_DIR` | `oms-demo` | No | `/var/data/uploads` |

`PORT` is supplied by Render. `CORS_ALLOWED_HOST` is unnecessary for the
recommended one-service deployment. The current authentication uses an HTTP-only
session cookie. The login response also contains a short-lived Bearer JWT for
non-browser API clients; set `OMS_JWT_SECRET` to a random value of at least 32
characters in persistent deployments.

| Component | Render resource | Plan | Public URL | Notes |
| --- | --- | --- | --- | --- |
| OMS UI + API | Web Service (`oms-demo`) | Free for a temporary demo | `https://oms-demo.onrender.com` | One origin; starts after idle, files are ephemeral. |
| TiDB Cloud Starter | External MySQL-compatible database | Free quota | Not public | Recommended for the demo; TLS-only public database endpoint. |
| Object storage | None for demo | — | — | Runtime uploads are temporary; use a paid disk or later object-storage integration if persistence is needed. |

OMS is a Kotlin Multiplatform system for monitoring projects, SIR inspections, procurement, financial records, documents and map markers.

## Local verification scenario

1. Start MySQL: `docker compose up -d`.
2. Start the backend: `./gradlew :server:run` (Windows: `./gradlew.bat :server:run`). Flyway applies the current schema and reference procurement records. Migration `V27` resets disposable project, inspection, financial, document and non-admin user data before creating the TVET II reference hierarchy; procurement reference records are retained.
3. Sign in as `admin` / `password`. The application keeps the session in the `oms_session` cookie.
4. In **Administration**, create a project manager and an inspector. Sign in under each role to verify that administration and financial monitoring are restricted to authorised roles.
5. As a project manager, create a project with a budget and map coordinates; optionally add a subproject with contract amount and planned dates. Verify that both appear in the project list, on the map and on the dashboard.
6. Upload a project document. Create an inspection report, add photos and a finding, then submit the report for review. As a project manager, approve it or return it for revision with a reason.
7. Add a financial record, export the project financial records to XLSX and import the generated file back. Confirm the result in **Financial Monitoring**.
8. Open **Procurement** and verify that the reference procurement records load. Use the dashboard, map and project cards to confirm that the data is consistent across the system.

### Local web UI

Run the backend first. The web UI uses `http://localhost:8080/api/v1`; its local CORS policy permits the Compose development server on port 8081. Sign in as `admin` / `password`, then create a project through **Projects** and confirm that it is retrieved from the API rather than mocked data.

`server/requests.http` contains ready-to-run API examples. Runtime files are kept in `server/uploads/` and are intentionally not stored in Git.

## Build and Run

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:

- for the Wasm target (faster, modern browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
      ```
- for the JS target (slower, supports older browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:jsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
      ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
