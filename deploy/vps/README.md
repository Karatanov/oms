# UMITAF OMS VPS deployment

This Compose file is intended for `/docker/oms` on the production VPS. It uses
MySQL 8.4, named volumes `oms-mysql-data` and `oms-uploads`, and exposes no
database or application ports to the Internet. Traefik discovers the OMS
container through Docker labels and routes `https://ua-oms.com` to port 8080
inside the private `oms-internal` network.

Before the first `docker compose up`, copy `.env.example` to `.env`, generate
unique values for all secret placeholders, and restrict the file to mode 0600.
The image must be pulled from GHCR after the corresponding GitHub Actions image
workflow succeeds.

## Automatic production deployment

`Publish OMS container` deploys each successful `master` build automatically
using its immutable `sha-<commit>` tag. The repository must define these
Actions secrets:

- `VPS_SSH_PRIVATE_KEY`: the private key authorized for the production host;
- `VPS_SSH_KNOWN_HOSTS`: the pinned `known_hosts` entry for the production host.

The workflow uses its short-lived `GITHUB_TOKEN` to pull the private image, so
no permanent GHCR token is stored on the VPS. `deploy.sh` waits for the new
container to become healthy, verifies its revision label, and restores the
previous `.env` and container if deployment fails. The public HTTPS health
endpoint is checked before the workflow succeeds.
