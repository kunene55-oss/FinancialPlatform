# Financial Platform

Microservice financial platform: ingests transactions, processes them against account balances, and serves aggregated reporting. Backed by Kafka, Postgres, Keycloak (auth), HashiCorp Vault (dynamic DB creds), and Spring Cloud Config Server (central config). Fronted by a Spring Cloud Gateway edge service; deployable via Docker Compose (single environment) or Helm (dev/qa/prod).

## Running Locally (Docker Compose)

Prereqs: Docker with Compose, JDK 21.

**1. First time only — create the Docker secrets** (gitignored; `.example` files show the expected format):
```bash
cp secrets/postgres_password.txt.example secrets/postgres_password.txt
cp secrets/vault_root_token.txt.example secrets/vault_root_token.txt
cp secrets/configserver_password.txt.example secrets/configserver_password.txt
# edit each to a real value
```

**2. Start everything:**
```bash
docker compose up -d --build
```
Every service's `Dockerfile` is a multi-stage build — it compiles from source (installing `common_module` first, then packaging the service) inside the image itself, so `--build` alone always reflects whatever's on disk. No separate `mvnw` step needed. First build per service takes a few minutes (empty `.m2` cache inside the build stage); subsequent builds are fast via Docker layer caching unless that service's or `common_module`'s source changed.

`vault-init` occasionally races Postgres's first-boot restart cycle (the official Postgres image runs initdb, then restarts once — `pg_isready` can report healthy in the gap) and exits with an error on a *completely fresh* volume. If that happens, Postgres is already stable by then — just rerun `docker compose up -d` and it goes through.

**3. Verify it's up:**
```bash
docker compose ps                              # everything should be "healthy" or "Up"
curl http://localhost:8080/actuator/health      # api-gateway
curl http://localhost:8081/actuator/health      # ingestion-service
curl http://localhost:8082/actuator/health      # processing-service
curl http://localhost:8083/actuator/health      # aggregation-service
```

**4. Take it down:**
```bash
docker compose down
```
Postgres data lives in the named volume `postgres-data` — survives a plain `down`, so `account_number_seq` and everything else keeps counting forward across restarts instead of resetting. Use `docker compose down -v` if you actually want a clean slate (`postgres/init.sql` only reruns then, since it's an "on first init" script). Keycloak has no persistent volume — it fully re-imports [keycloak/realm-export.json](keycloak/realm-export.json) on every fresh start, so realm state (including anything you change by hand through its UI) does *not* survive a restart.

### Starting individual services

`docker compose up -d --build <service>` also starts whatever that service `depends_on` (e.g. asking for `ingestion-service` brings up `kafka-1/2/3`, `keycloak`, `configserver`, `postgres`, `vault-init` too). Add `--no-deps` to start only the named service, e.g. when everything else is already running and you only touched one.

| Service | Command |
|---|---|
| Zookeeper node | `docker compose up -d zookeeper-1` (or `-2`/`-3`) |
| Kafka broker | `docker compose up -d --build kafka-1` (or `-2`/`-3`) |
| Postgres | `docker compose up -d postgres` |
| Vault | `docker compose up -d vault` |
| Keycloak | `docker compose up -d keycloak` |
| Config server | `docker compose up -d --build configserver` |
| Ingestion service | `docker compose up -d --build ingestion-service` |
| Processing service | `docker compose up -d --build processing-service` |
| Aggregation service | `docker compose up -d --build aggregation-service` |
| API gateway | `docker compose up -d --build api-gateway` |

Useful companions: `docker compose logs -f <service>` to tail one service, `docker compose ps` for status/health.

### Resource requirements

The stack now runs a 3-broker Kafka cluster on a 3-node Zookeeper ensemble (see [Kafka](#kafka) below) alongside 5 JVM app services, Postgres, Keycloak, and Vault — roughly 13 JVM-ish containers. Each Kafka/Zookeeper container has an explicit `KAFKA_HEAP_OPTS` cap (512M for brokers, 256M for Zookeeper nodes) to keep the footprint reasonable, but this is still meaningfully heavier than the old single-broker setup. If `docker compose up` hangs or the Docker daemon itself becomes unresponsive partway through, it's usually the host running out of memory, not a bug in the compose file:
- **Windows/WSL2**: increase the memory available to Docker Desktop's VM via `%UserProfile%\.wslconfig`:
  ```ini
  [wsl2]
  memory=5GB
  swap=2GB
  ```
  then `wsl --shutdown` and relaunch Docker Desktop to apply it. Leave enough headroom for Windows itself — don't allocate the whole host.
- **macOS/Linux**: raise the memory limit in Docker Desktop's Resources settings (or the Linux daemon's cgroup limits) similarly.

## Services & Ports

| Service | Host Port | Container Port | Purpose |
|---|---|---|---|
| api-gateway | 8080 | 8080 | Edge routing — single entry point for the 3 app services |
| ingestion-service | 8081 | 8080 | Accepts transactions (single + CSV bulk), publishes to Kafka |
| processing-service | 8082 | 8080 | Owns accounts/balances, consumes Kafka, applies transactions |
| aggregation-service | 8083 | 8080 | Read-only reporting/analytics API over transactions |
| configserver | — (internal 8888) | 8888 | Spring Cloud Config Server, native/classpath-backed |
| keycloak | 8180 | 8080 | Auth (realm `financial-platform`) |
| vault | 8200 | 8200 | Dynamic Postgres credentials via AppRole |
| postgres | 5432 | 5432 | Single DB `transactions`, shared by all 3 services |
| kafka-1, kafka-2, kafka-3 | — (internal 9092 each) | 9092 | 3-broker event bus, `kafka-1:9092,kafka-2:9092,kafka-3:9092` inside compose network |
| zookeeper-1, zookeeper-2, zookeeper-3 | — | 2181 | 3-node Kafka coordination ensemble |

All app services run on Spring Boot default port 8080 internally — no `server.port` override in any of them.

## Data Flow

```
client → api-gateway (8080) ─┬→ /ingest/**       → ingestion-service → Kafka topic "transactions.raw"
                              │                                              ↓
                              │                                     processing-service (consumer, group "processing-service")
                              │                                              ↓ writes
                              ├→ /accounts/**     → processing-service   Postgres: clients, transactions tables
                              │                                              ↑ reads (SELECT only)
                              └→ /aggregations/** → aggregation-service
```

- **api-gateway**: Spring Cloud Gateway, routes `/ingest/**` → ingestion-service, `/accounts/**` → processing-service, `/aggregations/**` → aggregation-service (route table in `configserver/src/main/resources/config/api-gateway.yml`). Validates the JWT issuer + `azp` before forwarding (cheap early reject for garbage/foreign tokens) — each backend still independently enforces its own `azp` + role checks, the gateway doesn't replace that. No routes are rewritten; backend path prefixes are used as-is.
- **ingestion-service**: `POST /ingest/ingestEvent`, `POST /ingest/fileUpload` (CSV), `GET /ingest/files/{fileHash}`, `GET /ingest/files/{fileHash}/failed-payments`. Writes `files` / `failed_payment_reports` tables itself; publishes `TransactionReceivedEvent` (common_module) to Kafka topic `transactions.raw`, keyed by `accountId`. Producer: `acks=all`, idempotent, infinite retries.
- **processing-service**: consumes `transactions.raw`. Applies deposit/withdrawal/transfer against `clients.balance` with optimistic locking (`@Version` on `ClientEntity`/`TransactionEntity`). Transfers create paired `TRANSFER_OUT`/`TRANSFER_IN` rows; self-transfer rejected. Bad/invalid messages go to dead-letter topic (`transactions.raw.DLT`) via `DeadLetterPublishingRecoverer` + exponential backoff (1s → max 10s elapsed); validation errors skip retry and go straight to DLT. REST: `/accounts/createAccount`, `/deleteAccount/{id}`, `/closeAccount/{id}`, `/suspendAccount/{id}`, `/freezeAccount/{id}` — all `account-admin` role.
- **aggregation-service**: pure read API, no Kafka involvement at all (no Kafka dependency in its pom). Reads `transactions` table only (`ddl-auto=none`, no writes). Endpoints: `/aggregations/{accountId}/summary|trend|totals|merchants|status-summary|transactions`, plus multi-account `/aggregations/summary?accountIds=...`. Access is either staff (`account-read`/`account-admin`) or self-service owner (`account-owner` + `account_ids` claim match via `AccountAccessGuard`). `/trend`'s `interval` param takes the `TrendInterval` enum name (`DAILY`/`WEEKLY`/`MONTHLY`), not the SQL field it maps to (`day`/`week`/`month`).

## Database

Single Postgres instance, single database `transactions` (host port 5432). Schema created by [postgres/init.sql](postgres/init.sql):
- `transactions` — the shared ledger table (aggregation reads it, processing writes it)
- `clients` — accounts/balances (processing-service owns)
- `files`, `failed_payment_reports` — ingestion-service owns
- `account_number_seq` — sequence for account numbers, starts at 10000001

No static per-service DB users exist in init.sql. Each service instead gets **short-lived credentials from Vault** at startup (default TTL 4h, max 24h), scoped to only the tables it needs:

| Service | Vault role | Grants |
|---|---|---|
| ingestion-service | `ingestion-service-db` | SELECT/INSERT/UPDATE/DELETE on `files`, `failed_payment_reports` |
| processing-service | `processing-service-db` | SELECT/INSERT/UPDATE/DELETE on `transactions`, `clients`; USAGE/SELECT on `account_number_seq` |
| aggregation-service | `aggregation-service-db` | SELECT only on `transactions` |

## Kafka

- Cluster: 3 brokers (`kafka-1`, `kafka-2`, `kafka-3`, `confluentinc/cp-kafka` 7.5.0), each internal-only on `9092`, no host port published. Coordinated by a 3-node Zookeeper ensemble (`zookeeper-1/2/3`, `confluentinc/cp-zookeeper` 7.5.0) — `ZOOKEEPER_SERVER_ID` + `ZOOKEEPER_SERVERS` peer list on each node, one leader elected among the three. Clients use the full broker list as `bootstrap-servers` (`kafka-1:9092,kafka-2:9092,kafka-3:9092`) so they can still connect if any single broker is down.
- Replication: broker defaults `KAFKA_DEFAULT_REPLICATION_FACTOR=3` / `KAFKA_MIN_INSYNC_REPLICAS=2`, plus the same RF=3/min-ISR=2 explicitly on the internal offsets and transaction-state topics. The app topic (`transactions.raw`) doesn't rely on those defaults + auto-create — it's provisioned explicitly via a `NewTopic` bean in `ingestion-service`'s `KafkaProducerConfig` (1 partition, `replicas=3`, `min.insync.replicas=2` set at the topic level). Combined with the producer's `acks=all` + idempotence, a write is durable across the loss of one broker.
- Still a single-host setup: HA here means broker/replica loss, not host loss — all 3 brokers run as containers on the one Docker Compose host. Genuine multi-host HA would mean a real k8s Kafka deployment (StatefulSet, PVs, pod anti-affinity) or a managed Kafka service — neither exists yet for this project (see [Deployment](#deployment)).
- Topic `transactions.raw` (1 partition — not yet increased for consumer parallelism, a separate decision from replication): producer = ingestion-service, consumer = processing-service (`groupId=processing-service`).
- Message contract: `TransactionReceivedEvent` from `common_module` (shared library, groupId `com.example.financial`, artifact `common_module`), JSON-serialized. aggregation-service has zero Kafka dependency.
- **Known gap**: nothing consumes the dead-letter topic (`transactions.raw.DLT`, see below) — messages that land there just accumulate, with no reprocessing job or alerting.

## Auth (Keycloak)

Realm `financial-platform`, port 8180 (host) / 8080 (container). Realm import from [keycloak/realm-export.json](keycloak/realm-export.json).

- Clients: `ingestion-service`, `processing-service`, `aggregation-service` (confidential, client-credentials/service-account only) + `customer-portal` (public, standard flow, for end users).
- Each backend service enforces the `azp` (authorized-party) claim in its `SecurityConfig`/custom `JwtDecoder` — rejects tokens not issued for it. **Exception**: aggregation-service's validator accepts `azp` in `{aggregation-service, processing-service, customer-portal}`, and merges Keycloak client roles from *every* `resource_access` bucket in the token, not just its own — deliberate, per its own comment, because roles can live under either service's namespace.
- **api-gateway** sits in front and accepts `azp` in `{ingestion-service, processing-service, aggregation-service, customer-portal}` — the union of everything the three routed services individually accept. This is a cheap early reject, not a trust boundary change: each backend still runs its own strict `azp`/role check on the forwarded request.
- Roles: `processing-service` → `account-admin`, `account-read`; `ingestion-service` → `transaction-ingest`; `aggregation-service` → `transaction-aggregate`, `account-read`, `account-admin`; `customer-portal` → `account-owner`.
- Client-scope `account-access` maps user attribute `account_ids` into token claim `account_ids`, used for owner-level self-service checks (`AccountAccessGuard`).
- Client-scope `roles` (`oidc-usermodel-client-role-mapper`) is what actually puts `resource_access.<client>.roles` on a token — required by every `SecurityConfig`'s role check in this repo. `realm-export.json` is a hand-authored partial realm, not a full Keycloak export, so this scope isn't created for free the way it would be in a realm Keycloak provisioned itself; it has to be defined explicitly and referenced via each client's `defaultClientScopes`.
- Defining a client role (under `roles.client`) does not grant it to anyone — a service's own `client_credentials` token only carries a role if its service-account user (`users[].serviceAccountClientId`) explicitly lists it under `clientRoles`. `ingestion-service` had the `transaction-ingest` role defined but nobody granted — its own token got `403` on every `/ingest/*` endpoint until a `service-account-ingestion-service` user entry was added. Worth checking for the same gap before assuming a client can call its own endpoints.
- `/actuator/health` is `permitAll` on all 4 app services (everything else stays behind JWT auth) — needed so k8s liveness/readiness probes, which can't present a token, don't crash-loop the pods. configserver has no way to do the same cleanly (default Spring Security basic-auth-everything, no custom `SecurityConfig`), so its Helm chart uses a `tcpSocket` probe instead — same workaround the docker-compose healthcheck already used.

**Known issue**: the `aggregation-service` client secret in realm-export.json is identical to `processing-service`'s secret (copy/paste, not intentional shared-secret design). Combined with aggregation-service accepting multiple `azp` values, this weakens the intended service-to-service isolation — worth a fix.

**Known issue**: `demo-customer` password-grant login fails with `invalid_grant: Account is not fully set up` — some required action is implicitly defaulting to enabled for realm users despite `"temporary": false` on the credential. Not yet root-caused; blocks testing the `customer-portal`/`account-owner` self-service path end-to-end.

## Config Server

Spring Cloud Config Server, port 8888 (internal only, no host mapping), **native/classpath-backed** (not git) — serves files bundled at `configserver/src/main/resources/config/`. Secured with HTTP Basic (`CONFIG_SERVER_USER=configclient`, password from Docker secret `configserver_password`). All four app services pull their config from here via `spring.config.import=optional:configserver:...`.

Each service has a base file (`processing-service.yml`, `ingestion-service.yml`, `aggregation-service.yml`, `api-gateway.yml`) plus profile overlays (`-dev.yml`, `-qa.yml`, `-prod.yml`) that Spring Cloud Config's native resolver merges on top when the client requests that profile — standard `{application}-{profile}.yml` convention. What's in the base file vs. an overlay is a deliberate split:

- **Base file, same in every environment**: datasource URL, Kafka bootstrap servers, Keycloak issuer-uri, business config (ingestion row limits, gateway route table). These use the compose-network hostnames (`postgres`, `kafka-1`/`kafka-2`/`kafka-3`, `keycloak`, `configserver`) and are expected to resolve identically in any environment — see [helm/README.md](helm/README.md) for why the Helm charts keep Service names matching those exact hostnames instead of templating around it.
- **Profile overlay, differs per environment**: `spring.jpa.show-sql` (`true` in dev, `false` in qa/prod), `logging.level.com.example.financial` (`DEBUG`/`INFO`/`WARN`), and for `api-gateway`, `gateway.cors.allowed-origins`.

Which overlay a service pulls is controlled by `SPRING_PROFILES_ACTIVE`, set per environment: `dev` hardcoded in [docker-compose.yaml](docker-compose.yaml), and an `env: [{name: SPRING_PROFILES_ACTIVE, value: <env>}]` entry in each app chart's `values-<env>.yaml` for Helm. `configserver` itself doesn't consume a profile — it's the one serving them.

## Vault

Dev-mode Vault (`hashicorp/vault:1.17`), port 8200, root token from Docker secret. `vault-init` (one-shot container, runs before app services) via [vault-init/setup-approle.sh](vault-init/setup-approle.sh):
1. Enables the `database` secrets engine, connects to Postgres as admin (`user`), registers the 3 dynamic roles listed above.
2. Enables AppRole auth; per service, writes a policy (`read` on `database/creds/<service>-db` + lease renew/revoke) and an AppRole bound to it (`token_ttl=1h`, `secret_id_ttl=24h`, unlimited uses).
3. Writes each service's `role_id`/`secret_id` to the shared `vault-approle` Docker volume at `/vault/approle/<service>-role-id` / `-secret-id`.

Each app service's `entrypoint.sh` reads those files into `VAULT_ROLE_ID`/`VAULT_SECRET_ID` env vars before launch, then authenticates via AppRole and pulls its DB credentials dynamically (`spring.cloud.vault.database`).

## Common Module

`common_module` (Maven, `com.example.financial:common_module`) — shared library depended on by all 3 app services. Holds the Kafka message contract:
- `TransactionReceivedEvent` — `transactionId`, `accountId`, `amount`, `type`, `status`, `timestamp`, `publishedAt`, `transferId`
- `TransactionType` enum — `DEPOSIT, TRANSFER, TRANSFER_IN, TRANSFER_OUT, WITHDRAWAL`
- `TransactionStatus` enum — `RECEIVED, PROCESSING, PROCESSED, FAILED`

## Startup Order

Enforced via `depends_on` + healthchecks in [docker-compose.yaml](docker-compose.yaml):

```
zookeeper-1/2/3 → kafka-1/2/3 (all 3 healthy) ─┐
postgres ────────────────────────────────────┼→ vault → vault-init ─┐
configserver ─────────────────────────────────┤                     ├→ ingestion-service, processing-service (need all 3 kafka brokers too)
keycloak ─────────────────────────────────────┘                     └→ aggregation-service (no kafka dependency)
                                                                                     ↓
                                                                                api-gateway
```

`vault-init` must reach `service_completed_successfully` before any app service starts, since they all need the AppRole credential files it writes. `ingestion-service` and `processing-service` wait on all 3 Kafka brokers being `service_healthy`, not just one. `api-gateway` waits on `keycloak` + `configserver` (healthy) and the three app services (started) — it has no DB/Vault dependency of its own.

## Deployment

- **Docker Compose** ([docker-compose.yaml](docker-compose.yaml)): single environment, everything on one Docker network, as described throughout this doc. `docker compose up`.
- **Kubernetes / Helm** ([helm/](helm/)): one chart per service (`configserver`, `ingestion-service`, `processing-service`, `aggregation-service`, `api-gateway`) sharing a `common` library chart, with `values-dev.yaml` / `values-qa.yaml` / `values-prod.yaml` per chart controlling replicas, resources, image tag, autoscaling, and (for `api-gateway`) ingress host. Only `api-gateway` is meant to be reachable from outside the cluster. Postgres/Kafka/Keycloak/Vault aren't charted here — see [helm/README.md](helm/README.md) for what each environment needs to provide and the secrets to pre-create before installing.

## Load/Smoke Testing

[k6/](k6/) — smoke, load, and spike test scripts, all driving traffic through `api-gateway` only (same constraint every real deployment has). Same scripts run against docker-compose or any deployed environment by overriding `BASE_URL`/`KEYCLOAK_URL`/client secrets. See [k6/README.md](k6/README.md).
