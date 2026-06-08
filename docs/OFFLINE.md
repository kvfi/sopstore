# Air-gapped install guide

sopstore is designed to run on a host with **no internet access**. This
document covers building artefacts on a connected machine and bringing them
to the offline host.

## 1. Build on a connected machine

```bash
# JDK 25 + Docker required.

# Build the runtime jar and a Maven offline repo of all deps.
./gradlew bootJar
./gradlew --write-locks                                  # pin transitives
./gradlew dependencies --write-verification-metadata sha256  # checksum manifest

# Build the digest-pinned application image.
docker build -t sopstore:0.1.0 -f deploy/Dockerfile .

# Pull and save dependency images.
docker pull postgres:16.4
docker pull redis:7.4-alpine
docker pull minio/minio:RELEASE.2025-03-12T18-04-18Z

docker save sopstore:0.1.0 -o sopstore-app-0.1.0.tar
docker save postgres:16.4 -o postgres-16.4.tar
docker save redis:7.4-alpine -o redis-7.4.tar
docker save minio/minio:RELEASE.2025-03-12T18-04-18Z -o minio.tar
```

Verify your digest pins match the registry — image owners occasionally
re-publish a tag:

```bash
docker inspect --format='{{index .RepoDigests 0}}' postgres:16.4
docker inspect --format='{{index .RepoDigests 0}}' redis:7.4-alpine
```

Update `deploy/docker-compose.yml` and `deploy/Dockerfile` `FROM` lines if
the digests differ before building.

### Transfer set

Copy to the air-gapped host (USB stick / approved transfer medium):

```
sopstore/                          # the repo
sopstore-app-0.1.0.tar             # docker save output
postgres-16.4.tar
redis-7.4.tar
minio.tar
gradle-cache.tar.gz                # ~/.gradle/caches (optional — for rebuilds offline)
```

## 2. Install on the offline host

### 2a. docker compose path

```bash
docker load -i sopstore-app-0.1.0.tar
docker load -i postgres-16.4.tar
docker load -i redis-7.4.tar
docker load -i minio.tar

cd sopstore
cp .env.example .env
# Set SESSION_SECRET to a fresh random value (or SPRING_PROFILES_ACTIVE=onprem).

docker compose -f deploy/docker-compose.yml up -d
```

### 2b. Kubernetes / Helm path

```bash
ctr -n=k8s.io images import sopstore-app-0.1.0.tar
ctr -n=k8s.io images import postgres-16.4.tar

helm install sopstore deploy/helm/sopstore \
  --set image.repository=sopstore \
  --set image.tag=0.1.0 \
  --set deploymentMode=onprem \
  --set database.url=jdbc:postgresql://postgres:5432/sopstore \
  --set sessionSecret=$(openssl rand -base64 48)
```

## 3. Verifying the install is air-tight

### 3a. Browser DevTools — no external requests

1. Open the app in a browser.
2. Open DevTools → **Network**. Filter on **Domain**.
3. Click through login, procedure list/detail, run mode, training, dashboards.
4. Confirm the only requests are to your sopstore host.

### 3b. CSP is enforced

```bash
curl -I http://localhost:8080/login | grep -i content-security-policy
```

Must print `default-src 'self'; ...` per `application.yml` → `sopstore.security.csp`.

### 3c. No-network smoke test

```bash
# Linux example — block egress for the app's UID:
sudo iptables -I OUTPUT -m owner --uid-owner sopstore -j REJECT
curl http://localhost:8080/actuator/health/readiness
# Restore:
sudo iptables -D OUTPUT -m owner --uid-owner sopstore -j REJECT
```

Readiness returns 200; the UI continues to serve. OTel exporters are off in
the `onprem` profile so the app does not retry against unreachable
collectors.

### 3d. Asset audit

```bash
grep -RIn -E "https?://" src/main/resources/static src/main/resources/templates \
    | grep -v -E "(w3.org/2000/svg|example\\.com)"
```

The only remaining hits should be SVG namespaces and example email
placeholders. No `cdn.*`, `googleapis.*`, `googletagmanager.com`, etc.

## 4. Health checks

- `GET /actuator/health/liveness` — process is alive (no DB call).
- `GET /actuator/health/readiness` — DB + Redis reachable; 503 otherwise.
- `GET /actuator/info` — build version, deployment mode.

## 5. PHASE-STATUS callout

The air-gapped pipeline above is **scaffolded but not yet validated against a
real air-gapped Kubernetes cluster**. The Helm chart skeleton lives in
`deploy/helm/sopstore/` and needs end-to-end testing in Phase 7. See
`docs/PHASE-STATUS.md`.
