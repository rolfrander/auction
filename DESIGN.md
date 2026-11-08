# Design – auksjon

Functional requirements are in [SPEC.md](SPEC.md). This document records the
technical design: stack, components, how they run in dev and prod, external
dependencies and configuration.

## 1. Architecture overview

```
               browser
   ┌──────────────┴───────────────┐
   │ public catalog (/)           │ admin SPA (/admin/*)
   │ plain DOM                    │ React
   └──────┬───────────────────────┘
          │ JSON /api/*                      direct POST upload (presigned form)
          ▼                                  │      image GET (public-read)
   ┌─────────────────────────────┐           ▼           ▲
   │ backend (Clojure, Jetty)    │──────► Object Storage (S3 API)
   │  /api  JSON + Swagger       │  SigV4 policy, delete
   │  /     static frontend/dist │
   └──────────────┬──────────────┘
                  │ JDBC
                  ▼
   SQLite file (dev, self-hosted)  |  PostgreSQL = Scaleway Serverless SQL (prod)
```

One process serves both the API and the built frontend, so prod is a single
container with a single origin (no CORS between app and API; cookie is
`SameSite=Strict`). Images bypass the backend: the browser uploads straight to
Object Storage and loads them from there.

## 2. Tech stack and key choices

### Backend (`backend/`, Clojure 1.12, JDK 21, tools.deps)

| Concern | Library | Notes |
|---|---|---|
| HTTP server | ring-jetty-adapter 1.13 | binds `0.0.0.0:$PORT` |
| Routing, coercion | reitit 0.7 + malli | request schemas are closed maps → unknown keys give 400 |
| JSON | muuntaja / jsonista | camelCase keys on the wire |
| OpenAPI | reitit-swagger(-ui) | spec `/api/swagger.json`, UI `/api/docs` |
| DB access | next.jdbc + honeysql | all access behind the `auksjon.db/Store` protocol |
| DB drivers | sqlite-jdbc, postgresql (pgjdbc 42.7) | picked by `DB_URL` prefix |
| Pooling | HikariCP 7 (Postgres only) | max 4 conns, idle conns dropped so a scale-to-zero DB can sleep |
| Object storage | AWS SDK v2 S3 (path-style, no Netty) | `ImageStore` protocol; in-memory impl for tests |
| Upload signing | own `images/post_policy.clj` | SigV4 POST policy, no SDK presigner (POST policies aren't supported by it) |
| Auth | own `auth.clj` | PBKDF2-SHA256 password hash, stateless HMAC session cookie |
| Tests | clojure.test, cognitect test-runner, ring-mock | |
| Build | tools.build → AOT uberjar (`build.clj`) | |

Design choices:
- **Storage abstraction.** Handlers only see `Store` / `ImageStore`
  protocols. `db/sql.clj` implements `Store` for SQLite and PostgreSQL with the
  same SQL (honeysql); the schema is created with `CREATE TABLE IF NOT EXISTS`
  at startup (tables `item`, `catalog`, `catalog_item` with `position` for
  ordering and `ON DELETE CASCADE`). No migration tool yet. Add one when the
  schema has to change in a way that isn't additive.
- **Stateless auth.** One admin user from config. The session token is an HMAC
  keyed from `ADMIN_PASSWORD_HASH`, so there is no session store, it works with
  several container instances, and changing the password logs everyone out.
  Cookie `auksjon_session`: HttpOnly, `Path=/api`, `SameSite=Strict`, 30 days.
  `auth/wrap-admin-auth` is the outermost middleware on `/api/admin/*` (before
  body parsing) and returns plain 401.
- **Direct-to-S3 uploads.** Keeps large image bodies out of the small (128 MB
  heap) backend. The policy pins exact key, content type, size ≤ 25 MB,
  `acl=public-read`, 10 min expiry. `POST /api/admin/images` (multipart through
  the backend) is kept as a fallback.
- **Config.** `config.clj` merges real env vars over `backend/.env`. No
  config library.
- **Startup failures** are printed as one line (`Startup failed: <cause chain>`)
  and exit 1, because the Scaleway console truncates stack traces.

### Frontend (`frontend/`, TypeScript 5.9 strict, Vite 8, Node 22)

Multi-page Vite build with two entries:

| App | Entry | Stack |
|---|---|---|
| Public catalog | `index.html` → `src/main.ts` | plain DOM, no framework (small, fast on phones) |
| Print list | `list/index.html` → `src/list.ts` | plain DOM; `/list/#<catalogId>` table (nr, tittel, prisantydning, startpris, notater) for print; uses admin endpoints when logged in (hidden catalogs too), else public |
| Admin | `admin/index.html` → `src/admin/main.tsx` | React 19, React Router 8 (basename `/admin`), TanStack Query 5, Tailwind 4, dnd-kit (reordering) |

- `src/api.ts` holds all `fetch("/api/…")` wrappers with explicit response
  types, and both apps use it.
- Admin server state goes only through hooks in `src/admin/queries.ts`.
- Admin UI text is Norwegian bokmål.

## 3. API surface

All under `/api`, JSON only.

| Path | Auth | Purpose |
|---|---|---|
| `GET /api/health` | – | liveness |
| `GET /api/catalogs`, `GET /api/catalogs/:id/items` | – | visible catalogs and their items |
| `POST /api/login`, `POST /api/logout` | – | set / clear session cookie |
| `GET /api/admin/session` | cookie | current user |
| `/api/admin/catalogs[/:id[/items[/:item-id]]]` | cookie | catalog CRUD, membership, order |
| `/api/admin/items[/:id]` | cookie | item CRUD, delete all |
| `POST /api/admin/images/upload-form` | cookie | presigned S3 POST form `{uploadUrl, fields, url}` |
| `POST /api/admin/images` | cookie | multipart upload via backend (fallback) |
| `GET /api/swagger.json`, `/api/docs` | – | OpenAPI + Swagger UI |

Outside `/api`, the backend serves `STATIC_DIR` and falls back to
`admin/index.html` for `/admin/*`. `openapi.json` at the repo root is an
exported copy of the spec.

## 4. Running

### Dev

Two processes. Vite proxies `/api` to the backend, so the browser sees one
origin:

| Component | Command (dir) | Port |
|---|---|---|
| Backend REPL | `clojure -M:dev -r` or jack in with `:dev`, then `(start)` (`backend/`) | 3000 |
| Backend, no REPL | `clojure -M:run` (`backend/`) | 3000 |
| Frontend | `npm run dev` (`frontend/`) | 5173 (open this one) |

- `(start)` rebuilds the handler per request, so re-evaluated code takes effect
  without a restart. `(stop)` and `(restart)` are also available.
- The default DB is SQLite `backend/auksjon.db`. Images still need an S3
  bucket. Any S3-compatible store works (Scaleway, MinIO, Garage).
- The dev container (`.devcontainer/`) has JDK 21, the Clojure CLI, Node 22,
  the scw CLI, jq and the host's Docker. It forwards ports 3000 and 5173.

### Prod

- **Single container.** `Dockerfile`, multi-stage:
  1. `node:22-alpine` runs the Vite build.
  2. `clojure:temurin-21-tools-deps` runs `clojure -T:build uber` (AOT).
  3. `eclipse-temurin:21-jre` is the runtime, running as a non-root user.
- **Runtime settings:** `PORT=3000`, `STATIC_DIR=/app/static`, `-Xmx128m`,
  SerialGC, C1 only (fast start, low memory). The SQLite default
  (`/data/auksjon.db`, volume `/data`) is only for self-hosting. On Scaleway,
  `DB_URL` points to Serverless SQL.
- **Local prod-style run:** `npm run build` (frontend), then `clojure -M:run`
  (backend), or `docker run -p 3000:3000 -v auksjon-data:/data --env-file backend/.env auksjon`.

## 5. Scaleway services (region fr-par)

| Service | Resource | Used for |
|---|---|---|
| Container Registry | namespace `auksjon` (private), image `rg.fr-par.scw.cloud/auksjon/auksjon:<git-sha>[-dirty-<ts>]` | deploy artifacts, rollback by tag |
| Serverless Containers | namespace `auksjon`, container `auksjon`, public, HTTPS only, port 3000, min 1 / max 2, 512 MB, 280 mvCPU | runs the app |
| Serverless SQL Database | db `auksjon`, PostgreSQL, cpu 0–4 (scales to zero) | all non-image data |
| Object Storage | bucket `S3_BUCKET`, objects `public-read`, CORS allowing POST from the app origin | images |
| IAM | application + API key, managed by hand | S3 and DB credentials |
| Cockpit | – | container logs, read in the console |
| (optional) custom domain | `DOMAIN` in `deploy/config.env`, CNAME → container endpoint | |

Notes:
- `min-scale=1` avoids JVM cold starts on requests (about €6/month). The DB
  still scales to zero, and Hikari drops idle connections so it can.
- **Serverless SQL auth uses IAM:** user = the IAM *application ID* (a UUID,
  not the access key), password = that application's API secret key, over TLS
  (`sslmode=require`). The application needs
  **`ServerlessSQLDatabaseFullAccess`**. `ReadWrite` can't run
  `CREATE TABLE`, and the app creates its tables at startup.
- Object Storage needs object read/write/delete for the key in
  `S3_ACCESS_KEY_ID`. The S3 and DB keys may belong to different
  applications.
- Bucket CORS (needs a key allowed to change bucket config):
  `clojure -X auksjon.images.s3/configure-cors! :origins '["https://…"]'`.

## 6. Deploy (`deploy/`)

Scripts call the `scw` CLI (v2.65.x) with `jq`. Docker builds `linux/amd64`.

| File | Purpose |
|---|---|
| `config.env` (committed) | region, resource names, scaling/memory, `DOMAIN` |
| `secrets.env` (gitignored, mode 600; template `secrets.env.example`) | every key becomes a *secret* env var on the container |
| `scw.yaml` (gitignored) | scw CLI profile; use via `SCW_CONFIG_PATH=deploy/scw.yaml` |
| `setup.sh` | once, idempotent: registry namespace, Serverless SQL db, container namespace |
| `deploy.sh [tag]` | build, push `<registry>/auksjon:<sha>`, create/update container, wait for `ready`, attach `DOMAIN`. With a tag: redeploy that image (rollback) |

`DB_URL` is not a secret. `deploy.sh` derives it from the database endpoint
(`jdbc:postgresql://<host>:5432/<db>?sslmode=require`).

scw CLI quirks handled in `deploy.sh`:
- `memory-limit-bytes` only accepts G/GB units, with decimal values
  (`0.512GB`).
- `create` takes `https-connections-only`, but `update` takes
  `https-connection-only`.
- The endpoint is in `public_endpoint` (`domain_name` is null).
- Right after an update the old status is still reported, so the wait loop
  ignores it at first.
- `update` with an unchanged image doesn't roll out; use
  `scw container container redeploy <id>`.

## 7. Configuration (environment variables)

Read from the environment, falling back to `backend/.env` (`KEY=value` or `KEY: value`).

| Variable | Default | Used by |
|---|---|---|
| `PORT` | `3000` | Jetty |
| `STATIC_DIR` | `../frontend/dist` (image: `/app/static`) | static file serving |
| `DB_URL` | `jdbc:sqlite:auksjon.db` (image: `jdbc:sqlite:/data/auksjon.db`) | Store: `jdbc:sqlite:…` or `jdbc:postgresql:…` |
| `DB_USER`, `DB_PASSWORD` | – | Postgres credentials (Serverless SQL: application ID + secret key) |
| `S3_ENDPOINT` | required | e.g. `https://s3.fr-par.scw.cloud` |
| `S3_REGION` | `us-east-1` | SigV4 region, `fr-par` on Scaleway |
| `S3_BUCKET` | required | |
| `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` | required | SDK client and POST-policy signing |
| `S3_WEB_URL` | required | public base URL for image links (`<S3_WEB_URL>/<key>`) |
| `ADMIN_USER` | – | login username |
| `ADMIN_PASSWORD_HASH` | – | `pbkdf2-sha256$<iter>$<salt>$<hash>`, from `clojure -M:hash-password`; also keys session HMAC |
| `TEST_DB_URL` | – (SQLite) | tests only: run the suite against Postgres (truncates tables) |
| `SCW_CONFIG_PATH` | – | deploy scripts / scw CLI profile |

## 8. Commands

| What | Command |
|---|---|
| Backend tests | `clojure -X:test` (`backend/`) |
| Frontend build / typecheck | `npm run build` / `npm run typecheck` (`frontend/`) |
| Password hash | `clojure -M:hash-password` (`backend/`) |
| Uberjar | `clojure -T:build uber` (`backend/`) |
| Image | `docker build -t auksjon .` |
| Infra setup | `deploy/setup.sh` |
| Deploy / rollback | `deploy/deploy.sh` / `deploy/deploy.sh <tag>` |
| Container status/logs | `scw container container list`, logs in Cockpit (the CLI needs Cockpit read permission) |

Before committing: `clojure -X:test` and `npm run build` must pass.

## 9. External interfaces summary

- **Browsers:** HTTPS to the container (`/`, `/admin`, `/api`). Image uploads
  are POSTed to Object Storage, and images are loaded from `S3_WEB_URL`.
- **Object Storage:** S3 API (path-style, SigV4). PutObject/DeleteObject from
  the backend, and browser POST with a policy.
- **PostgreSQL wire protocol over TLS** to Serverless SQL.
- **Build and deploy:**
  - Docker registry `rg.fr-par.scw.cloud`, with blobs on
    `*.s3.fr-par.scw.cloud`.
  - Scaleway API through the scw CLI.
  - npm and Maven Central/Clojars, plus git deps from GitHub
    (`tools.build`, `test-runner`).
