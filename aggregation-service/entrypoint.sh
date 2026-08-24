#!/bin/sh
set -e
export VAULT_ROLE_ID="$(cat /vault/approle/aggregation-service-role-id)"
export VAULT_SECRET_ID="$(cat /vault/approle/aggregation-service-secret-id)"
export CONFIG_SERVER_PASSWORD="$(cat /run/secrets/configserver_password)"
exec java -jar app.jar
