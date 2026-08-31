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

**2. Build the jars.** The Dockerfiles `COPY` a pre-built jar rather than doing a multi-stage Maven build, so package everything before `docker compose build` — `common_module` first, the other five depend on it:
```bash
(cd common_module && ./mvnw -q -DskipTests install)
for svc in ingestion-service processing-service aggregation-service configserver api-gateway; do
  (cd "$svc" && ./mvnw -q -DskipTests package)
done
```

**3. Start everything:**
```bash
docker compose up -d --build
```
`vault-init` occasionally races Postgres's first-boot restart cycle (the official Postgres image runs initdb, then restarts once — `pg_isready` can report healthy in the gap) and exits with an error on a *completely fresh* volume. If that happens, Postgres is already stable by then — just rerun `docker compose up -d` and it goes through.

**4. Verify it's up:**
```bash
docker compose ps                              # everything should be "healthy" or "Up"
curl http://localhost:8080/actuator/health      # api-gateway
curl http://localhost:8081/actuator/health      # ingestion-service
curl http://localhost:8082/actuator/health      # processing-service
curl http://localhost:8083/actuator/health      # aggregation-service
```

**5. Take it down:**
```bash
docker compose down
```
Postgres data lives in the named volume `postgres-data` — survives a plain `down`, so `account_number_seq` and everything else keeps counting forward across restarts instead of resetting. Use `docker compose down -v` if you actually want a clean slate (`postgres/init.sql` only reruns then, since it's an "on first init" script). Keycloak has no persistent volume — it fully re-imports [keycloak/realm-export.json](keycloak/realm-export.json) on every fresh start, so realm state (including anything you change by hand through its UI) does *not* survive a restart.

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
| kafka | — (internal 9092) | 9092 | Event bus, `kafka:9092` inside compose network |
| zookeeper | — | 2181 | Kafka coordination |

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
- **aggregation-service**: pure read API, no Kafka involvement at all (no Kafka dependency in its pom). Reads `transactions` table only (`ddl-auto=none`, no writes). Endpoints: `/aggregations/{accountId}/summary|trend|totals|merchants|status-summary|transactions`, plus multi-account `/aggregations/summary?accountIds=...`. Access is either staff (`account-read`/`account-admin`) or self-service owner (`account-owner` + `account_ids` claim match via `AccountAccessGuard`).

  **Known issue**: every endpoint here except `/trend` (a native query) throws `could not determine data type of parameter` from Postgres when called without `from`/`to` — the JPQL `(:from IS NULL OR t.timestamp >= :from)` pattern gives Postgres's extended query protocol no type to infer for a null-valued parameter. `/trend`'s native query already works around this with `CAST(:from AS timestamp)`; the same fix was never applied to `AggregationRepository`'s other four queries. Since `from`/`to` are optional on every endpoint, this hits on the common case, not an edge case.

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

- Broker: `kafka:9092` internal (no host port published). Zookeeper-backed (`confluentinc/cp-kafka` 7.5.0), single-broker, replication factor forced to 1 (`KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`, `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1`) — dev/single-node only, not HA.
- Topic `transactions.raw`: producer = ingestion-service, consumer = processing-service (`groupId=processing-service`).
- Message contract: `TransactionReceivedEvent` from `common_module` (shared library, groupId `com.example.financial`, artifact `common_module`), JSON-serialized. aggregation-service has zero Kafka dependency.

## Auth (Keycloak)

Realm `financial-platform`, port 8180 (host) / 8080 (container). Realm import from [keycloak/realm-export.json](keycloak/realm-export.json).

- Clients: `ingestion-service`, `processing-service`, `aggregation-service` (confidential, client-credentials/service-account only) + `customer-portal` (public, standard flow, for end users).
- Each backend service enforces the `azp` (authorized-party) claim in its `SecurityConfig`/custom `JwtDecoder` — rejects tokens not issued for it. **Exception**: aggregation-service's validator accepts `azp` in `{aggregation-service, processing-service, customer-portal}`, and merges Keycloak client roles from *every* `resource_access` bucket in the token, not just its own — deliberate, per its own comment, because roles can live under either service's namespace.
- **api-gateway** sits in front and accepts `azp` in `{ingestion-service, processing-service, aggregation-service, customer-portal}` — the union of everything the three routed services individually accept. This is a cheap early reject, not a trust boundary change: each backend still runs its own strict `azp`/role check on the forwarded request.
- Roles: `processing-service` → `account-admin`, `account-read`; `ingestion-service` → `transaction-ingest`; `aggregation-service` → `transaction-aggregate`, `account-read`, `account-admin`; `customer-portal` → `account-owner`.
- Client-scope `account-access` maps user attribute `account_ids` into token claim `account_ids`, used for owner-level self-service checks (`AccountAccessGuard`).
- Client-scope `roles` (`oidc-usermodel-client-role-mapper`) is what actually puts `resource_access.<client>.roles` on a token — required by every `SecurityConfig`'s role check in this repo. `realm-export.json` is a hand-authored partial realm, not a full Keycloak export, so this scope isn't created for free the way it would be in a realm Keycloak provisioned itself; it has to be defined explicitly and referenced via each client's `defaultClientScopes`.
- `/actuator/health` is `permitAll` on all 4 app services (everything else stays behind JWT auth) — needed so k8s liveness/readiness probes, which can't present a token, don't crash-loop the pods. configserver has no way to do the same cleanly (default Spring Security basic-auth-everything, no custom `SecurityConfig`), so its Helm chart uses a `tcpSocket` probe instead — same workaround the docker-compose healthcheck already used.

**Known issue**: the `aggregation-service` client secret in realm-export.json is identical to `processing-service`'s secret (copy/paste, not intentional shared-secret design). Combined with aggregation-service accepting multiple `azp` values, this weakens the intended service-to-service isolation — worth a fix.

**Known issue**: `demo-customer` password-grant login fails with `invalid_grant: Account is not fully set up` — some required action is implicitly defaulting to enabled for realm users despite `"temporary": false` on the credential. Not yet root-caused; blocks testing the `customer-portal`/`account-owner` self-service path end-to-end.

## Config Server

Spring Cloud Config Server, port 8888 (internal only, no host mapping), **native/classpath-backed** (not git) — serves files bundled at `configserver/src/main/resources/config/`. Secured with HTTP Basic (`CONFIG_SERVER_USER=configclient`, password from Docker secret `configserver_password`). All four app services pull their config from here via `spring.config.import=optional:configserver:...`.

Each service has a base file (`processing-service.yml`, `ingestion-service.yml`, `aggregation-service.yml`, `api-gateway.yml`) plus profile overlays (`-dev.yml`, `-qa.yml`, `-prod.yml`) that Spring Cloud Config's native resolver merges on top when the client requests that profile — standard `{application}-{profile}.yml` convention. What's in the base file vs. an overlay is a deliberate split:

- **Base file, same in every environment**: datasource URL, Kafka bootstrap servers, Keycloak issuer-uri, business config (ingestion row limits, gateway route table). These use the compose-network hostnames (`postgres`, `kafka`, `keycloak`, `configserver`) and are expected to resolve identically in any environment — see [helm/README.md](helm/README.md) for why the Helm charts keep Service names matching those exact hostnames instead of templating around it.
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
zookeeper → kafka ─┐
postgres ──────────┼→ vault → vault-init ─┐
configserver ───────┤                     ├→ ingestion-service, processing-service (needs kafka too)
keycloak ───────────┘                     └→ aggregation-service (no kafka dependency)
                                                          ↓
                                                     api-gateway
```

`vault-init` must reach `service_completed_successfully` before any app service starts, since they all need the AppRole credential files it writes. `api-gateway` waits on `keycloak` + `configserver` (healthy) and the three app services (started) — it has no DB/Vault dependency of its own.

## Deployment

- **Docker Compose** ([docker-compose.yaml](docker-compose.yaml)): single environment, everything on one Docker network, as described throughout this doc. `docker compose up`.
- **Kubernetes / Helm** ([helm/](helm/)): one chart per service (`configserver`, `ingestion-service`, `processing-service`, `aggregation-service`, `api-gateway`) sharing a `common` library chart, with `values-dev.yaml` / `values-qa.yaml` / `values-prod.yaml` per chart controlling replicas, resources, image tag, autoscaling, and (for `api-gateway`) ingress host. Only `api-gateway` is meant to be reachable from outside the cluster. Postgres/Kafka/Keycloak/Vault aren't charted here — see [helm/README.md](helm/README.md) for what each environment needs to provide and the secrets to pre-create before installing.
