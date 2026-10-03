# UMITAF OMS — production CI/CD

## What a green deployment means

The authoritative workflow is **Production deployment** in GitHub Actions.
On every push to `master`, it runs these visible stages in order:

1. **Tests — backend** — Ktor/Flyway regression tests against an ephemeral MySQL 8.4 service.
2. **Tests — frontend** — Kotlin browser tests plus Chromium, WebKit and Firefox regression checks.
3. **Build production image** — builds `linux/amd64` and publishes
   `ghcr.io/karatanov/oms:sha-<full Git SHA>` (and the convenience `latest` tag).
4. **Deploy Hostinger** — securely pulls that exact immutable tag and replaces only the `oms` service.
5. **Verify production over HTTPS** — calls `https://ua-oms.com/health` until it receives `UP` and the exact triggering Git SHA.

A green final deployment therefore means the exact master commit in that run is healthy at `https://ua-oms.com`. GitHub Pages remains a legacy preview only; Render is not used by this pipeline.

The workflow has a single non-cancelling release queue. This is intentional: a deployment already replacing a production container is never interrupted, and an older job cannot race a newer one.

## Build identity and health

The Docker build injects two public, non-sensitive values into the runtime image:

```json
{"status":"UP","gitSha":"<40-character SHA>","buildTime":"<UTC ISO-8601 time>"}
```

They are available from both `/health` and `/api/v1/health`. The runtime image does not include `.git`; the SHA comes only from CI build arguments.

## Required GitHub configuration

Create a GitHub Environment named **production**. It may be left without an approval rule for fully automatic deployment; add required reviewers later if a manual gate is desired.

Create these repository or environment secrets. Never commit their values.

| Secret | Purpose |
| --- | --- |
| `PROD_HOST` | Hostinger VPS hostname or IP address. |
| `PROD_USER` | Dedicated non-root deployment user. |
| `PROD_SSH_KEY` | Private SSH key for that deployment user. |
| `PROD_SSH_KNOWN_HOSTS` | Pinned `known_hosts` line for `PROD_HOST`, obtained independently with `ssh-keyscan` and verified out of band. |
| `PROD_SSH_PORT` | SSH port; use `22` when omitted. |

Optionally set repository variable `PROD_DEPLOY_DIR`; its default is `/docker/oms`.

The VPS must already be logged in to GHCR with a read-only package credential:

```bash
docker login ghcr.io
```

This credential stays on the VPS and is not sent through GitHub Actions. The image package must permit that credential to read `ghcr.io/karatanov/oms`.

## One-time VPS preparation

Do this manually before enabling the workflow. It does not change Traefik or delete data.

1. Create a dedicated deploy user and install its public key. Add it to the `docker` group only if needed. **Docker group membership is root-equivalent control of the VPS**, so use a dedicated key and restrict access accordingly.
2. Keep existing root/password access until key login has been tested in a separate terminal. Do not disable owner access as part of this procedure.
3. Create `/docker/oms`, copy `deploy/vps/.env.example` to `/docker/oms/.env`, supply production secrets, and set mode `0600`.
4. Copy `deploy/vps/docker-compose.yml` and `scripts/deploy-production.sh` once to the same directory; the workflow refreshes only these two non-secret deployment files on future deploys.
5. Confirm `oms-mysql-data` and `oms-uploads` are the current persistent volumes. Back both up before the first cutover.
6. Verify Traefik remains separately managed and that `oms-internal` is available. The OMS compose file exposes neither MySQL nor port 8080 to the public Internet.
7. Verify the deploy user can run `docker compose ps` and can pull the private GHCR image.

The deployment script pulls and restarts only `oms` with `--no-deps`. It never deletes volumes, rewrites `.env`, recreates MySQL, removes Traefik, uses `docker system prune`, or runs Flyway repair. Flyway migrations still run normally during application startup; migration history is not altered automatically.

## Manual recovery and rollback

Use **Actions → Rollback production → Run workflow** and enter the full, lowercase 40-character SHA of a previously published release. The workflow accepts only that strict format, deploys `sha-<SHA>`, and verifies the public health response. It preserves MySQL, uploads, `.env`, and Traefik.

Rollback changes application code only. Flyway migrations are forward-only. Do not treat rollback as safe for an incompatible or irreversible migration: restore a compatible database and upload backup if the schema cannot run the older image.

If Actions is unavailable, a deploy user may run on the VPS:

```bash
OMS_DEPLOY_DIR=/docker/oms \
OMS_IMAGE=ghcr.io/karatanov/oms \
OMS_IMAGE_TAG=sha-<full Git SHA> \
/docker/oms/scripts/deploy-production.sh
curl --fail --silent https://ua-oms.com/health
```

## Diagnosing a failed deployment

- **Tests or image build failed:** open the failed job in GitHub Actions; production was not touched.
- **Deploy Hostinger failed:** check SSH host-key/user access and `docker compose ps` on the VPS. The workflow deliberately uses strict host-key checking.
- **Health verification failed:** inspect `docker compose logs --tail=100 oms`, confirm Traefik routes `ua-oms.com`, and compare `/health`'s `gitSha` with the Actions run SHA.
- **Migration failure:** do not use `OMS_FLYWAY_REPAIR` in production as a quick fix. Preserve logs and take a backup before an intentional recovery.

Production deployment status, image tag and external verification result are written into the GitHub Actions job summary.
