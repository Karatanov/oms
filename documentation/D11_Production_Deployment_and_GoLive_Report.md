# OMS — D11 Production Deployment & Go-Live Report

**Release:** OMS 1.0.0 / `master` / D0–D12 handover baseline  
**Go-live status:** READY FOR CUTOVER AFTER HOST DOCKER REVALIDATION

## Scope and release components

The release packages the Compose Web/Wasm UI and Ktor API in one Docker image, with MySQL 8.4, Flyway migrations and filesystem-backed MVP upload storage. Mobile distribution is not packaged by this repository deployment.

## Production-like deployment procedure

1. Copy `.env.example` to an untracked `.env`; replace all placeholders, including `MYSQL_ROOT_PASSWORD`, `DATABASE_PASSWORD` and `OMS_JWT_SECRET` (32+ random characters).
2. Run `docker compose up -d --build` from the repository root. The Docker multi-stage build creates the Java 21 server distribution and packaged Wasm UI from source.
3. Wait for `mysql` and `oms` health checks. Verify `GET /health` and `GET /api/v1/health` on `http://localhost:${OMS_PORT:-8080}`.
4. Flyway initializes an empty MySQL database and applies all ordered migrations. Do not modify applied migrations.
5. Run the release smoke checks: login, dashboard, projects, project details, SIR, financials, documents and administration. Validate each representative role at the API layer.

## Image-backed Render deployment

For the Render free-tier service, use the repository's GitHub Actions workflow `.github/workflows/publish-ghcr.yml` to build the same multi-stage production image outside Render. A successful `master` run publishes `ghcr.io/karatanov/oms:latest` and an immutable commit-SHA tag for `linux/amd64`, with GitHub Actions layer caching enabled.

Use `render.image.yaml.example` when creating an image-backed replacement service. Add a Render workspace registry credential named `ghcr-oms` with GitHub username `Karatanov` and a token restricted to `read:packages`; no registry token is stored in GitHub Actions, Render YAML or application configuration. Copy the current service's environment variables, health-check path and any disk configuration. Render image-backed services do not detect a changed `latest` tag automatically, so an operator performs **Manual Deploy → Deploy latest reference** after CI is green. This is intentional: publishing a container never consumes Render minutes or restarts the live service without an explicit deploy decision.

## Validation evidence

- A fresh Java 21 distribution was built from source, packaged into an isolated Docker image, and started with MySQL 8.4 as the integrated OMS UI/API.
- Empty-database Flyway migration applied V1-V29; both `/health` and `/api/v1/health` returned `UP`.
- The JVM bytecode target is pinned to Java 21, matching the runtime image. This fixed a discovered Java 24/21 startup mismatch.
- D9 integration/UAT covered authentication, five roles, PM isolation, activation lifecycle and API authorization.
- D10 verified that the packaged Web UI loads as part of the Ktor service.
- D11 corrected Docker Compose: no tracked passwords, application service, dependency health checks and persistent MySQL/uploads volumes are now part of the stack. Runtime logging defaults to INFO.
- The final portable multi-stage Dockerfile repeats the verified `:server:installDist` build inside its Java 21 builder stage. Its last clean rebuild reached that Gradle stage, but Docker Desktop then lost its BuildKit connection and entered a WSL data-VHD attach error (`WSL_E_USER_VHD_ALREADY_ATTACHED`). Re-run `docker compose build` after the host Docker/WSL state is repaired; no project compilation failure was reported.

## Security and configuration

All secrets are external environment values. The repository contains only placeholders. `OMS_JWT_SECRET` must be configured for persistent deployments; otherwise tokens are invalidated on process restart. The one-service public deployment uses same-origin UI/API; configure `CORS_ALLOWED_HOST` only for a separately hosted frontend.

## Storage, backup and recovery

Uploads are served from the `oms-uploads` filesystem volume. Every accepted document, SIR workbook, inspection photo and thumbnail is also mirrored to MySQL so an ephemeral Render deployment can restore a missing local file on first access. Back up both `mysql-data` and `oms-uploads` before production migration or release; the mirror is a resilience fallback, not a replacement for normal backups. No MinIO/S3 service is deployed.

## Rollback

Roll back the application image/configuration to the last verified version. Database migrations are forward-only: do not attempt an ad-hoc schema downgrade. If a migration must be rolled back, restore the pre-release MySQL backup and the matching upload-volume backup, then deploy the previous image.

## Go-live checklist

- [x] Release build, backend tests and Web/Wasm compilation verified in D9/D10.
- [x] Docker image and isolated MySQL rehearsal completed from an empty database.
- [x] Flyway empty-database path validated.
- [x] Health endpoint, authentication and RBAC smoke coverage completed.
- [x] Compose configuration uses external secrets and persistent volumes.
- [x] Deployment and recovery procedure documented.
- [ ] Repeat the final multi-stage Docker build after repairing the current workstation's Docker Desktop/WSL VHD attachment state.
- [ ] Production secrets, host, DNS/TLS and cutover approval supplied by deployment owner.
- [ ] Production database and upload-volume backups confirmed immediately before cutover.

## Known limitations and sign-off

This release is **ready for cutover**, not marked as an actual production deployment: no production host, credentials, DNS/TLS authority or cutover approval are present in the repository. The deployment owner must also repeat the final multi-stage Docker build after repairing the current host's Docker Desktop/WSL VHD state. SMTP delivery remains a local activation `.eml` outbox, and durable object storage remains filesystem volumes for the approved MVP.
