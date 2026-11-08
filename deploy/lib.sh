# Shared helpers for setup.sh / deploy.sh. Sourced, not executed.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"
SECRETS_FILE="$DEPLOY_DIR/secrets.env"

log() { printf '\033[1m==> %s\033[0m\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# Read KEY=value lines literally (no expansion) into an associative array.
#   read_env_file <file> <array-name>
read_env_file() {
  local file=$1 line k v
  local -n out=$2
  [[ -f $file ]] || return 0
  while IFS= read -r line || [[ -n $line ]]; do
    [[ $line =~ ^[[:space:]]*(#|$) ]] && continue
    [[ $line =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]] || continue
    k=${BASH_REMATCH[1]}
    v=${BASH_REMATCH[2]}
    v=${v%"${v##*[![:space:]]}"}            # trim trailing whitespace
    v=${v#"${v%%[![:space:]]*}"}            # trim leading whitespace
    [[ $v =~ ^\"(.*)\"$ || $v =~ ^\'(.*)\'$ ]] && v=${BASH_REMATCH[1]}
    out[$k]=$v
  done < "$file"
}

# Set KEY=value in secrets.env (replace or append).
set_secret() {
  local k=$1 v=$2 tmp
  touch "$SECRETS_FILE"; chmod 600 "$SECRETS_FILE"
  tmp=$(mktemp)
  grep -v "^$k=" "$SECRETS_FILE" > "$tmp" || true
  printf '%s=%s\n' "$k" "$v" >> "$tmp"
  mv "$tmp" "$SECRETS_FILE"; chmod 600 "$SECRETS_FILE"
}

declare -A CFG=()
read_env_file "$DEPLOY_DIR/config.env" CFG
REGION=${CFG[REGION]:-fr-par}

command -v scw >/dev/null || die "scw CLI not found (https://github.com/scaleway/scaleway-cli)"
command -v jq  >/dev/null || die "jq not found"

scwj() { scw "$@" region="$REGION" -o json; }

# First id whose .name matches exactly, from a JSON array on stdin.
id_by_name() { jq -r --arg n "$1" 'map(select(.name == $n)) | first | .id // empty'; }

registry_endpoint() {
  scwj registry namespace list name="${CFG[REGISTRY_NAMESPACE]}" \
    | jq -r --arg n "${CFG[REGISTRY_NAMESPACE]}" 'map(select(.name == $n)) | first | .endpoint // empty'
}
container_namespace_id() {
  scwj container namespace list name="${CFG[CONTAINER_NAMESPACE]}" | id_by_name "${CFG[CONTAINER_NAMESPACE]}"
}
database_id() {
  scwj sdb-sql database list name="${CFG[DB_NAME]}" | id_by_name "${CFG[DB_NAME]}"
}

# jdbc:postgresql://host:port/db?sslmode=require from the database endpoint
# (postgres[ql]://[user@]host:port/db?...).
database_jdbc_url() {
  local ep
  ep=$(scwj sdb-sql database get "$(database_id)" | jq -r '.endpoint')
  [[ $ep =~ ^postgres(ql)?://([^@/]*@)?([^/?]+)/([^?]+) ]] || die "unexpected database endpoint: $ep"
  printf 'jdbc:postgresql://%s/%s?sslmode=require\n' "${BASH_REMATCH[3]}" "${BASH_REMATCH[4]}"
}
