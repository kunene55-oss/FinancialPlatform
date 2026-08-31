#!/bin/bash
set -euo pipefail

# Deletes every fixture account this suite could have created - both
# seed.sh's direct-SQL rows and any probe accounts a script created through
# the real API (see smoke.js's "create account" check) - by tag rather
# than by the fixtures.json ID list, so it also mops up orphans left by an
# interrupted run.

CONTAINER="${POSTGRES_CONTAINER:-financialplatform-postgres-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Deleting k6 fixture accounts (first_name='K6', last_name='Fixture')..."
docker exec -i "$CONTAINER" psql -U user -d transactions -c \
  "DELETE FROM clients WHERE first_name = 'K6' AND last_name = 'Fixture';"

rm -f "$SCRIPT_DIR/fixtures.json"
echo "Done."
