# Deployment Guide — Keycloak Enterprise

Deploy the custom Keycloak fork with PostgreSQL on a Linux server using Docker Compose.

## Prerequisites

| Requirement | Minimum version | Check |
|---|---|---|
| Docker Engine | 24+ | `docker --version` |
| Docker Compose plugin | v2.20+ | `docker compose version` |
| JDK (to build) | 17, 21, or 25 | `java -version` |

> **Note:** Docker Compose v2 uses `docker compose` (space), not `docker-compose` (hyphen).

---

## Step 1 — Build the distribution

Rebuild the Maven distribution archive to ensure it contains all latest code changes. Run this from the repository root on your build machine (not necessarily the deployment server):

```bash
# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Build the Quarkus distribution (~5–10 min first run, faster with Maven cache)
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
```

This produces `quarkus/dist/target/keycloak-999.0.0-SNAPSHOT.tar.gz` (~174MB). The `.dockerignore` ensures only this file enters the Docker build context.

> Run this step every time you make code changes before rebuilding the Docker image.

---

## Step 2 — Build the Docker image

```bash
docker compose build
```

The build has two stages:

1. **Builder** (`eclipse-temurin:25-jdk`): extracts the tar.gz and runs `kc.sh build --db=postgres` to perform Quarkus augmentation — bakes the PostgreSQL driver, HTTP configuration, and health endpoints into bytecode. The JDK version must match the one used to build the distribution (JDK 25).
2. **Runtime** (`eclipse-temurin:25-jre`): copies only the augmented distribution. No JDK or build tools in the final image.

Expected build time: 3–5 minutes (most time is Quarkus augmentation in stage 1).

To rebuild with no Docker layer cache (e.g. after upgrading the base image):

```bash
docker compose build --no-cache
```

---

## Step 3 — Configure .env

The `.env` file at the repository root holds all runtime secrets and settings. It is gitignored and must be created on the deployment server.

Edit the values — every line marked `CHANGE_ME` must be updated:

```bash
nano .env
```

| Variable | What to set |
|---|---|
| `DB_PASSWORD` | Strong random password (min 20 chars) |
| `KC_HOSTNAME` | Your public hostname, e.g. `sso.example.com` (no `https://`, no trailing slash) |
| `BOOTSTRAP_ADMIN_PASSWORD` | Strong password for the initial admin account |

The `BOOTSTRAP_ADMIN_*` variables are **first-time only**. After Keycloak initialises the master realm admin, it ignores these variables on every subsequent start. Change the admin password in the Admin Console after first login.

---

## Step 4 — Start services

```bash
docker compose up -d
```

Docker Compose will:
1. Start PostgreSQL and wait until `pg_isready` succeeds (healthcheck).
2. Start Keycloak only after PostgreSQL is confirmed healthy.
3. On the first start, Keycloak runs Liquibase database migrations (30–90 seconds).

Watch logs during the first start:

```bash
docker compose logs -f keycloak
```

Look for:
```
Keycloak 999.0.0-SNAPSHOT on JVM (powered by Quarkus ...) started in ...
```

---

## Step 5 — Health check

```bash
# From the deployment server (management port is not exposed externally):
curl http://localhost:8080/health/ready
```

Expected response:

```json
{"status": "UP", "checks": [...]}
```

Verify the login redirect works:

```bash
curl -sI http://localhost:8080/ | grep -i location
# Expected: Location: http://<hostname>/realms/master/protocol/openid-connect/auth?...
```

---

## Step 6 — Reverse proxy (nginx)

Keycloak runs HTTP on port 8080. Your reverse proxy must terminate TLS and forward requests, setting the `X-Forwarded-Proto: https` header so Keycloak generates correct redirect URIs.

**nginx example** (`/etc/nginx/sites-available/keycloak`):

```nginx
server {
    listen 443 ssl http2;
    server_name sso.example.com;

    ssl_certificate     /etc/letsencrypt/live/sso.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sso.example.com/privkey.pem;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   X-Forwarded-Host  $host;
        proxy_buffer_size         128k;
        proxy_buffers             4 256k;
        proxy_busy_buffers_size   256k;
    }
}

# Redirect HTTP → HTTPS
server {
    listen 80;
    server_name sso.example.com;
    return 301 https://$host$request_uri;
}
```

```bash
nginx -t && systemctl reload nginx
```

> The `X-Forwarded-Proto: https` header is required. Without it, Keycloak generates `http://` redirect URIs and OIDC flows will break.

---

## Backup volume

The Backup & Restore feature stores ZIP files at `/opt/keycloak/data/backups` inside the container. This path is inside the `keycloak_data` named volume, so backups survive container restarts and upgrades.

```bash
# List backup files
docker compose exec keycloak ls /opt/keycloak/data/backups

# Copy all backups to the host
docker cp "$(docker compose ps -q keycloak)":/opt/keycloak/data/backups ./keycloak-backups
```

---

## Upgrading

When you update the code and want to redeploy:

```bash
# 1. Rebuild the distribution (Step 1)
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install

# 2. Rebuild the Docker image
docker compose build

# 3. Recreate the Keycloak container (PostgreSQL and its data are unaffected)
docker compose up -d --force-recreate keycloak
```

To tag images for rollback, set `IMAGE_TAG=1.1.0` in `.env` before each build.

---

## Useful commands

```bash
# View live logs
docker compose logs -f

# Stop all services (data preserved in named volumes)
docker compose down

# Stop and delete all data (WARNING: irreversible)
docker compose down -v

# Open a shell in the Keycloak container
docker compose exec keycloak bash

# Show effective Keycloak configuration
docker compose exec keycloak /opt/keycloak/bin/kc.sh show-config

# Check container status and health
docker compose ps
```

---

## Production hardening notes

- **Secrets**: For higher security, use Docker Secrets (`secrets:` in compose) or a vault instead of plaintext `.env` values.
- **Log retention**: Add logging limits to prevent unbounded disk use:
  ```yaml
  logging:
    driver: "json-file"
    options:
      max-size: "100m"
      max-file: "5"
  ```
- **Database backups**: The `keycloak_data` volume holds application-level backup ZIPs. Your PostgreSQL data (realm configs, users, sessions) lives in `postgres_data` — back it up with `pg_dump`.
- **Hostname strict mode**: If you see redirect URI mismatches, verify `KC_HOSTNAME` in `.env` exactly matches the hostname your browser connects to (no port suffix, no trailing slash).
