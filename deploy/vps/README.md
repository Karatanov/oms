# UMITAF OMS VPS deployment

This Compose file is intended for `/docker/oms` on the production VPS. It uses
MySQL 8.4, named volumes `oms-mysql-data` and `oms-uploads`, and exposes no
database or application ports to the Internet. Traefik discovers the OMS
container through Docker labels and routes `https://ua-oms.com` to port 8080
inside the private `oms-internal` network.

Before the first `docker compose up`, copy `.env.example` to `.env`, generate
unique values for all secret placeholders, and restrict the file to mode 0600.
The `Production deployment` workflow pulls an immutable SHA-tagged image from
GHCR and replaces only the `oms` service. It preserves these named volumes and
never manages Traefik. Follow [Production CI/CD](../../documentation/PRODUCTION_CICD.md)
for the required GitHub secrets and one-time VPS setup.
