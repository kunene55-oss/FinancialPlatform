#!/bin/sh
set -e

export VAULT_TOKEN="$(cat /run/secrets/vault_root_token)"
PG_ADMIN_PASSWORD="$(cat /run/secrets/postgres_password)"

# --- Database secrets engine: dynamic, short-lived, per-service Postgres roles ---
vault secrets enable database 2>/dev/null || true

vault write database/config/postgres \
  plugin_name=postgresql-database-plugin \
  allowed_roles="ingestion-service-db,processing-service-db,aggregation-service-db" \
  connection_url="postgresql://{{username}}:{{password}}@postgres:5432/transactions" \
  username="user" \
  password="${PG_ADMIN_PASSWORD}"

vault write database/roles/ingestion-service-db \
  db_name=postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT USAGE ON SCHEMA public TO \"{{name}}\"; GRANT SELECT, INSERT, UPDATE, DELETE ON files TO \"{{name}}\";" \
  default_ttl=4h \
  max_ttl=24h

vault write database/roles/processing-service-db \
  db_name=postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT USAGE ON SCHEMA public TO \"{{name}}\"; GRANT SELECT, INSERT, UPDATE, DELETE ON transactions, clients TO \"{{name}}\"; GRANT USAGE, SELECT ON SEQUENCE account_number_seq TO \"{{name}}\";" \
  default_ttl=4h \
  max_ttl=24h

vault write database/roles/aggregation-service-db \
  db_name=postgres \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT USAGE ON SCHEMA public TO \"{{name}}\"; GRANT SELECT ON transactions TO \"{{name}}\";" \
  default_ttl=4h \
  max_ttl=24h

# --- AppRole auth: one identity per service, scoped to its own DB role only ---
vault auth enable approle 2>/dev/null || true

mkdir -p /vault/approle

for svc in ingestion-service processing-service aggregation-service; do
  cat <<EOF > /tmp/${svc}-policy.hcl
path "database/creds/${svc}-db" {
  capabilities = ["read"]
}
path "sys/leases/renew" {
  capabilities = ["update"]
}
path "sys/leases/revoke" {
  capabilities = ["update"]
}
EOF
  vault policy write ${svc}-policy /tmp/${svc}-policy.hcl

  vault write auth/approle/role/${svc}-role \
    token_policies="${svc}-policy" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=24h \
    secret_id_num_uses=0

  vault read -field=role_id auth/approle/role/${svc}-role/role-id > /vault/approle/${svc}-role-id
  vault write -f -field=secret_id auth/approle/role/${svc}-role/secret-id > /vault/approle/${svc}-secret-id
done

echo "Vault bootstrap complete (database secrets engine + AppRole)"
