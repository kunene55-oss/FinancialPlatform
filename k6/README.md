# k6 tests

Three test types, all driving traffic through `api-gateway` only — the same
constraint the real deployments have (everything else is `ClusterIP`-only
per [helm/README.md](../helm/README.md), so a script that only talks to the
gateway runs unmodified against docker-compose, dev, qa, or prod):

| Script | Purpose | Needs `seed.sh`? |
|---|---|---|
| `smoke.js` | 1 VU, 5 iterations. Health check + one full create→ingest→aggregate cycle. Run this first, always — it's meant to catch "is this environment obviously broken" before you bother with the other two. | No (creates its own probe account; skips ingest/aggregation checks with a warning if `fixtures.json` isn't present) |
| `load.js` | Ramping VUs, sustained hold, ramp down. Mixed ingest + aggregation-summary + aggregation-trend traffic against a pool of real accounts. Has real latency thresholds (`p95<800ms` per endpoint) and a tight error-rate threshold (`<1%`). | Yes |
| `spike.js` | Low baseline → sudden burst → hold → sudden drop → baseline again. Checks the system survives and recovers from a traffic spike rather than requiring zero errors during it (error-rate threshold is deliberately loose, `<25%`). | Yes |

## Why `seed.sh` exists

`POST /accounts/createAccount` is `void` (`ClientController.createClient`) —
it returns `200` with an empty body, so there's no way for any HTTP client,
k6 included, to learn the `accountId` it just assigned. `load.js` and
`spike.js` need a pool of *real* accountIds to drive ingest/aggregation
traffic against, so `seed.sh` provisions them directly via SQL — through
the same `account_number_seq` the app itself uses, so the IDs look exactly
like ones the app would assign — and writes them to `fixtures.json` for the
scripts to read. `cleanup.sh` removes them afterward (by tag, not by the
JSON list, so it also mops up anything an interrupted run left behind).

If you're adding a new scenario that needs accounts, read `fixtures.json`
the same way `load.js` does — don't try to create-and-guess.

## Running locally (docker-compose)

```bash
cd k6
./seed.sh 20                # creates 20 fixture accounts, writes fixtures.json

# native k6 binary:
k6 run smoke.js
k6 run load.js
k6 run -e SPIKE_PEAK_VUS=50 spike.js

# or via Docker, if you don't have k6 installed:
docker run --rm --network financialplatform_default \
  -e BASE_URL=http://api-gateway:8080 -e KEYCLOAK_URL=http://keycloak:8080 \
  -v "$(pwd):/scripts" grafana/k6 run /scripts/load.js

./cleanup.sh                 # deletes the fixture accounts + fixtures.json
```

Note the `docker run` example points `BASE_URL`/`KEYCLOAK_URL` at the
container network names, not `localhost` — that's because k6 itself is
running *inside* the docker network there. Running the native `k6` binary
on your host needs no such override; the defaults in `lib/config.js`
already point at `localhost:8080`/`localhost:8180`, matching docker-compose's
host port mappings.

(Windows/Git Bash: prefix the `docker run` command with `MSYS_NO_PATHCONV=1`
or the `/scripts/...` path argument gets mangled into a Windows path.)

## Running against dev/qa/prod

```bash
BASE_URL=https://api.qa.financial-platform.example.com \
KEYCLOAK_URL=https://<that env's keycloak> \
ADMIN_CLIENT_SECRET=<real secret, not the local dev default> \
INGEST_CLIENT_SECRET=<real secret, not the local dev default> \
  k6 run smoke.js
```

`seed.sh`/`cleanup.sh` assume a local `docker exec`-reachable Postgres
container (`POSTGRES_CONTAINER` env var, default
`financialplatform-postgres-1`) — for a remote environment, seed accounts
however you'd normally reach that environment's database, then point
`fixtures.json` at the same shape (`["10000001", "10000002", ...]`) by hand
or by adapting the script.

## Config reference (`lib/config.js`)

All overridable via `-e VAR=value` or an env var of the same name:

| Var | Default | |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | api-gateway |
| `KEYCLOAK_URL` | `http://localhost:8180` | |
| `REALM` | `financial-platform` | |
| `ADMIN_CLIENT_ID` / `ADMIN_CLIENT_SECRET` | `processing-service` / (local dev secret) | covers `/accounts/**` and `/aggregations/**` — processing-service's service account holds `account-admin` plus `account-read`/`account-admin` on aggregation-service |
| `INGEST_CLIENT_ID` / `INGEST_CLIENT_SECRET` | `ingestion-service` / (local dev secret) | covers `/ingest/**` — `transaction-ingest` is only ever granted to ingestion-service's own service account |

`load.js` and `spike.js` also take `LOAD_PEAK_VUS`/`LOAD_RAMP`/`LOAD_HOLD`
and `SPIKE_BASELINE_VUS`/`SPIKE_PEAK_VUS` respectively — see the top of
each file.

## Extending

`lib/api.js` has one wrapper per endpoint already covered; add more there
rather than building requests inline in a scenario file. `AggregationController.trend`'s
`interval` param takes the enum name (`DAILY`/`WEEKLY`/`MONTHLY`), not the
SQL field it maps to (`day`/`week`/`month`) — easy to get backwards, cost
us a full pass of `load.js` failing before we caught it.
