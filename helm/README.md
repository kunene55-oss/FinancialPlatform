# Helm charts

One chart per service (`configserver`, `ingestion-service`, `processing-service`,
`aggregation-service`, `api-gateway`), sharing a `common` library chart for the
Deployment/Service/HPA/ServiceAccount/Ingress templates. Only `api-gateway` is
meant to be reachable from outside the cluster — everything else stays
`ClusterIP` and is only reachable through the gateway's routes.

Not charted here: Postgres, Kafka, Keycloak, Vault. Each environment is
assumed to run its own instance of these, reachable inside the same
namespace at the plain service names `postgres`, `kafka`, `keycloak`,
`vault` — that's what the config-server's checked-in config
(`configserver/src/main/resources/config/*.yml`) hard-codes, and this
chart set deliberately doesn't touch it. Use whatever charts your
platform team already trusts for those (e.g. Bitnami) and name the
Services to match, or the app services won't find them.

## Why Service names aren't release-prefixed

Every service's config (pulled from configserver) hard-codes its peers'
DNS names — e.g. `http://configserver:8888`, `http://processing-service:8080`.
So `common`'s fullname helper intentionally ignores the Helm release name and
uses the chart name as-is. Install each chart with a release name if you like,
but the Service it creates will always be named after the chart.

## First-time setup per environment/namespace

Each service reads `CONFIG_SERVER_PASSWORD` from a mounted Secret at
`/run/secrets/configserver_password`, and the three data services also read
Vault AppRole credentials from `/vault/approle/<service>-role-id` /
`-secret-id`. Same file layout as the existing docker-compose entrypoint.sh
scripts — the container images are unchanged between compose and k8s.

Create the secrets before installing (names match each chart's
`values.yaml` defaults):

```bash
kubectl create secret generic configserver-credentials \
  --from-literal=password='<configserver password>'

for svc in ingestion-service processing-service aggregation-service; do
  kubectl create secret generic "${svc}-vault-approle" \
    --from-literal=role-id='<role-id>' \
    --from-literal=secret-id='<secret-id>'
done
```

In practice you'd source `role-id`/`secret-id` from the same Vault AppRole
bootstrap `vault-init/setup-approle.sh` already does — this just moves the
delivery mechanism from a docker volume to a k8s Secret.

## Installing

```bash
helm dependency build ./processing-service   # once per chart, after any change to common/

helm install processing-service ./processing-service -f ./processing-service/values-dev.yaml -n dev
helm install processing-service ./processing-service -f ./processing-service/values-qa.yaml  -n qa
helm install processing-service ./processing-service -f ./processing-service/values-prod.yaml -n prod
```

Repeat per chart. `values-<env>.yaml` only overrides what differs from
`values.yaml` (image tag, replicas, resources, autoscaling, ingress host) —
check `values.yaml` for the full set of knobs (probes, node
placement, extra env, etc).

`api-gateway`'s `values-*.yaml` ingress host is a placeholder domain —
change it before installing anywhere real.
