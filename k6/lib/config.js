// Every value here is overridable via -e VAR=value (or an environment
// variable of the same name) so the same scripts run unmodified against
// local docker-compose, or a deployed dev/qa/prod environment fronted by
// api-gateway's ingress.
//
// The client secrets default to this repo's local dev realm values
// (see keycloak/realm-export.json) purely for convenience running against
// docker-compose. For any real dev/qa/prod environment, override
// ADMIN_CLIENT_SECRET / INGEST_CLIENT_SECRET explicitly - never rely on
// the defaults outside local compose.
export const config = {
  BASE_URL: __ENV.BASE_URL || 'http://localhost:8080',
  KEYCLOAK_URL: __ENV.KEYCLOAK_URL || 'http://localhost:8180',
  REALM: __ENV.REALM || 'financial-platform',

  // processing-service's service account holds account-admin (for
  // /accounts/**) and account-read/account-admin on aggregation-service
  // (for /aggregations/**) - one identity covers both route groups.
  ADMIN_CLIENT_ID: __ENV.ADMIN_CLIENT_ID || 'processing-service',
  ADMIN_CLIENT_SECRET: __ENV.ADMIN_CLIENT_SECRET || 'processing-service-secret',

  // separate identity for /ingest/** - transaction-ingest is only ever
  // granted to ingestion-service's own service account.
  INGEST_CLIENT_ID: __ENV.INGEST_CLIENT_ID || 'ingestion-service',
  INGEST_CLIENT_SECRET: __ENV.INGEST_CLIENT_SECRET || 'ingestion-service-secret',
};
