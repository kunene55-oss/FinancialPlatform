#!/bin/bash
set -euo pipefail

# POST /accounts/createAccount is void (ClientController.createClient) - it
# returns 200 with an empty body, so there is no API path to learn the
# accountId it just assigned. k6's ingest/aggregation scenarios need real,
# known accountIds to drive traffic against, so this script seeds fixture
# accounts directly via SQL - through the same account_number_seq the app
# itself uses, so the IDs look exactly like ones the app would assign -
# and writes them to fixtures.json for the k6 scripts to read.
#
# Requires the stack to be up (docker compose up) and reachable via the
# `docker` CLI. Run once before load.js / spike.js; smoke.js also uses it
# if present, but seeds its own single account if it isn't.
#
# Usage: ./seed.sh [count]

COUNT="${1:-20}"
CONTAINER="${POSTGRES_CONTAINER:-financialplatform-postgres-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$SCRIPT_DIR/fixtures.json"

echo "Seeding $COUNT fixture accounts into $CONTAINER..."

RAW_IDS=$(docker exec -i "$CONTAINER" psql -U user -d transactions -t -A -c "
INSERT INTO clients (id, account_id, first_name, last_name, id_number, account_status, balance, version, created_at, updated_at)
SELECT
  gen_random_uuid(),
  nextval('account_number_seq')::text,
  'K6',
  'Fixture',
  (extract(epoch from clock_timestamp())::bigint * 1000000) + g,
  'OPEN',
  0,
  0,
  now(),
  now()
FROM generate_series(1, $COUNT) AS g
RETURNING account_id;
")

WRITTEN=0
{
  printf '['
  first=true
  while IFS= read -r id; do
    id="$(echo "$id" | tr -d '[:space:]')"
    # psql's "INSERT 0 N" completion tag can leak through even with -t on
    # some client versions - keep only lines that are purely digits.
    case "$id" in
      ''|*[!0-9]*) continue ;;
    esac
    if [ "$first" = true ]; then first=false; else printf ','; fi
    printf '"%s"' "$id"
    WRITTEN=$((WRITTEN + 1))
  done <<< "$RAW_IDS"
  printf ']'
} > "$OUT"

echo "Wrote $WRITTEN accountIds to $OUT"
