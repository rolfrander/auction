#!/usr/bin/env bash
# One-time (idempotent) infrastructure setup: registry namespace, Serverless SQL
# database, Serverless Containers namespace. IAM is managed by hand (see
# secrets.env.example).
# The container itself is created by deploy.sh (it needs a pushed image).
source "$(dirname "$0")/lib.sh"

PROJECT_ID=$(scw config get default-project-id)
ORG_ID=$(scw config get default-organization-id)
[[ -n $PROJECT_ID && -n $ORG_ID ]] || die "run 'scw init' first (needs default project/organization)"

log "Registry namespace ${CFG[REGISTRY_NAMESPACE]}"
if [[ -z $(registry_endpoint) ]]; then
  scwj registry namespace create name="${CFG[REGISTRY_NAMESPACE]}" is-public=false >/dev/null
fi
registry_endpoint

log "Serverless SQL database ${CFG[DB_NAME]}"
if [[ -z $(database_id) ]]; then
  scwj sdb-sql database create name="${CFG[DB_NAME]}" cpu-min=0 cpu-max="${CFG[DB_CPU_MAX]:-4}" >/dev/null
fi
for _ in $(seq 60); do
  status=$(scwj sdb-sql database get "$(database_id)" | jq -r .status)
  [[ $status == ready ]] && break
  sleep 5
done
[[ $status == ready ]] || die "database not ready (status: $status)"
database_jdbc_url

log "Container namespace ${CFG[CONTAINER_NAMESPACE]}"
if [[ -z $(container_namespace_id) ]]; then
  scwj container namespace create name="${CFG[CONTAINER_NAMESPACE]}" --wait >/dev/null
fi
container_namespace_id

log "Done. Fill in the rest of $SECRETS_FILE, then run deploy/deploy.sh"
