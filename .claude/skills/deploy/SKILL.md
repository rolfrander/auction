---
name: deploy
description: Deploy auksjon to Scaleway (Serverless Containers + Serverless SQL) with deploy/deploy.sh, verify the rollout, or roll back to an earlier image tag. Use when asked to deploy, redeploy, roll back, or check/debug the production container.
---

# Deploy auksjon to Scaleway

Background (services, IAM, env vars, scw CLI quirks): `DESIGN.md` §5–7.
Scripts: `deploy/setup.sh` (infra, once), `deploy/deploy.sh [tag]`.

Arguments: none = build current HEAD and deploy; `<tag>` = redeploy an
already pushed image (rollback, or retry without rebuilding).

## 1. Preflight

Run all checks and stop on the first failure:

1. Tools: `scw version` (expect 2.65.x), `jq`, `docker info`. In the sandbox
   scw may live in `~/.local/bin`; if missing, install the pinned release
   binary from `https://github.com/scaleway/scaleway-cli/releases/download/v2.65.2/scaleway-cli_2.65.2_linux_<arch>`
   and verify it against that release's `SHA256SUMS`.
2. `SCW_CONFIG_PATH` points to `deploy/scw.yaml` (set in the dev container; in
   the sandbox it must be exported in `/etc/sandbox-persistent.sh`). Check
   `scw config get default-project-id` is non-empty.
3. `deploy/secrets.env` exists and is mode 600 (`chmod 600` it). **Never print
   values.** Check key names only, using `grep -oE '^[A-Z0-9_]+='`. Required:
   `ADMIN_USER ADMIN_PASSWORD_HASH S3_ENDPOINT S3_REGION S3_BUCKET
   S3_ACCESS_KEY_ID S3_SECRET_ACCESS_KEY S3_WEB_URL DB_USER DB_PASSWORD`.
   `DB_USER` must match a UUID regex (IAM application ID). If it starts
   with `SCW` it's an access key, which is wrong. Stop and tell the user.
4. Without a tag: `clojure -X:test` (in `backend/`) and `npm run build`
   (in `frontend/`) pass. Note whether the tree is dirty. The tag will then be
   `<sha>-dirty-<timestamp>`, so suggest committing first.

## 2. Deploy

```bash
deploy/deploy.sh            # or: deploy/deploy.sh <tag>
```
Use a long timeout (up to 10 min) and filter the output, e.g.
`2>&1 | grep -v '^#' | tail -30`. On success the last line is the endpoint URL.

## 3. Verify

- `scw container container list` → container `auksjon` has status `ready`. A
  `ready` status means the DB connection and `CREATE TABLE IF NOT EXISTS`
  succeeded, because the app exits 1 if they fail.
- `curl -s <endpoint>/api/health` and `curl -s <endpoint>/api/catalogs`
  return 200 and JSON.
- Report the endpoint, the image tag, and the result of each check.

## 4. Rollback

List tags with `scw registry image list name=auksjon -o json` and then
`scw registry tag list image-id=<id>`. Run `deploy/deploy.sh <previous-tag>`.
If the image is unchanged, `update` does not roll out. Then run
`scw container container redeploy <container-id>` and poll
`scw container container get <id>` until the status leaves `updating`.

## Troubleshooting

| Symptom | Cause / action |
|---|---|
| HTTP 403 with body `Approval required for <host>` (registry login, blob HEAD, curl to the endpoint, apt) | Sandbox firewall. Tell the user the exact host and to run `sbx policy approval ls` / `respond` on the host, then stop. Typical hosts: `rg.fr-par.scw.cloud`, `scw-reg-prd-fr-par-distribution.s3.fr-par.scw.cloud`, `*.functions.fnc.fr-par.scw.cloud` |
| Status `error`: "unable to start OR not listening on port 3000" | App crashed. Its last log line is `Startup failed: <cause chain>`. `scw container container logs` needs Cockpit read permission. If that's missing, ask the user to read Cockpit / the console |
| PSQLException during `doAuthentication` | Wrong DB creds: `DB_USER` must be the application ID, and `DB_PASSWORD` that application's secret key |
| `permission denied` on `CREATE TABLE` | The IAM application needs `ServerlessSQLDatabaseFullAccess` (`ReadWrite` is not enough) |
| Local `docker run` gets EOFException in the TLS handshake to `*.pg.sdb.fr-par.scw.cloud:5432` | The sandbox blocks raw TCP 5432. The DB can't be tested from the sandbox; deploy and check the container instead |
| scw argument errors | See the scw quirks in DESIGN.md §6. Fix `deploy.sh` and update that list |
| Images upload fails in browser (CORS) | Bucket CORS must allow POST from the app origin: `clojure -X auksjon.images.s3/configure-cors! :origins '["https://<endpoint>"]'` |

## Don't

- Print, echo or log any secret value, or commit `deploy/secrets.env` or `deploy/scw.yaml`.
- Run `deploy/setup.sh` unless the infrastructure is missing (deploy.sh says
  "infrastructure missing").
- Create or delete Scaleway resources other than the `auksjon` container
  without asking.
