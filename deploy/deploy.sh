#!/usr/bin/env bash
# Build the image, push it to Scaleway Container Registry and create/update the
# serverless container with it (config + secrets from deploy/*.env).
#   deploy/deploy.sh            build, push, deploy
#   deploy/deploy.sh <tag>      deploy an already pushed tag (e.g. rollback)
source "$(dirname "$0")/lib.sh"

declare -A SECRETS=()
read_env_file "$SECRETS_FILE" SECRETS
for k in ADMIN_USER ADMIN_PASSWORD_HASH S3_ENDPOINT S3_REGION S3_BUCKET \
         S3_ACCESS_KEY_ID S3_SECRET_ACCESS_KEY S3_WEB_URL DB_USER DB_PASSWORD; do
  [[ -n ${SECRETS[$k]:-} ]] || die "$k missing in $SECRETS_FILE (see secrets.env.example)"
done

registry=$(registry_endpoint)
ns_id=$(container_namespace_id)
[[ -n $registry && -n $ns_id ]] || die "infrastructure missing, run deploy/setup.sh"

if [[ $# -gt 0 ]]; then
  tag=$1
else
  tag=$(git -C "$ROOT_DIR" rev-parse --short HEAD)
  [[ -z $(git -C "$ROOT_DIR" status --porcelain) ]] || tag="$tag-dirty-$(date +%Y%m%d%H%M%S)"
  image="$registry/${CFG[IMAGE_NAME]}:$tag"
  log "Building $image"
  docker build --platform linux/amd64 -t "$image" "$ROOT_DIR"
  log "Pushing"
  scw registry login region="$REGION" >/dev/null
  docker push "$image"
fi
image="$registry/${CFG[IMAGE_NAME]}:$tag"

args=(
  image="$image"
  port=3000
  privacy=public
  min-scale="${CFG[MIN_SCALE]:-1}"
  max-scale="${CFG[MAX_SCALE]:-2}"
  # scw CLI only accepts G/GB units here (decimal: 0.512GB = 512 MB)
  memory-limit-bytes="$(awk -v mb="${CFG[MEMORY_MB]:-512}" 'BEGIN { print mb / 1000 }')GB"
  mvcpu-limit="${CFG[MVCPU]:-280}"
  environment-variables.DB_URL="$(database_jdbc_url)"
)
for k in "${!SECRETS[@]}"; do args+=("secret-environment-variables.$k=${SECRETS[$k]}"); done

container_id=$(scwj container container list namespace-id="$ns_id" name="${CFG[CONTAINER_NAME]}" \
                 | id_by_name "${CFG[CONTAINER_NAME]}")
if [[ -z $container_id ]]; then
  log "Creating container ${CFG[CONTAINER_NAME]}"
  container_id=$(scwj container container create namespace-id="$ns_id" name="${CFG[CONTAINER_NAME]}" \
                   "${args[@]}" https-connections-only=true | jq -r .id)
  [[ $(scwj container container get "$container_id" | jq -r .status) == created ]] \
    && scwj container container redeploy "$container_id" >/dev/null
else
  log "Updating container ${CFG[CONTAINER_NAME]}"
  scwj container container update "$container_id" "${args[@]}" \
    https-connection-only=true >/dev/null  # sic: create says "connections", update "connection"
fi

log "Waiting for rollout"
# Right after an update the old status (e.g. a previous "error") is still
# reported; only accept a final status once the rollout started or after 60 s.
started=
for i in $(seq 120); do
  c=$(scwj container container get "$container_id")
  status=$(jq -r .status <<<"$c")
  [[ $status == ready || $status == error ]] || started=1
  [[ ($status == ready || $status == error) && ( -n $started || $i -gt 12 ) ]] && break
  sleep 5
done
[[ $status == ready ]] || die "container status: $status ($(jq -r '.error_message // ""' <<<"$c")) — see: scw container container logs $container_id"
endpoint=$(jq -r '(.public_endpoint // .domain_name) | sub("^https?://"; "")' <<<"$c")

if [[ -n ${CFG[DOMAIN]:-} ]]; then
  if ! scwj container domain list container-id="$container_id" \
       | jq -e --arg h "${CFG[DOMAIN]}" 'any(.[]; .hostname == $h)' >/dev/null; then
    log "Attaching ${CFG[DOMAIN]} (needs CNAME -> $endpoint.)"
    scwj container domain create container-id="$container_id" hostname="${CFG[DOMAIN]}" >/dev/null
  fi
fi

log "Deployed $image"
echo "https://$endpoint"
[[ -n ${CFG[DOMAIN]:-} ]] && echo "https://${CFG[DOMAIN]}"
exit 0
