# Auksjon

Web app with a Clojure backend (JSON API) and a TypeScript frontend (Vite): a public catalog page and a React admin app.

The app is specified in @SPEC.md; technical design (stack, Scaleway services, env vars) in @DESIGN.md

Keep your replies extremely concise and focus on conveying the key information. The user is an expert in clojure and only needs a terse summary of responses.

## Layout

```
backend/            Clojure (tools.deps)
  deps.edn          deps + aliases (:run, :dev, :test)
  src/auksjon/
    handler.clj     reitit router, middleware, coercion, swagger; all routes under /api
    auth.clj        login + signed session cookie for /api/admin/* (PBKDF2-SHA256 password hash), -main generates hashes
    main.clj        -main, builds store (env DB_URL, default jdbc:sqlite:auksjon.db) + S3 image store, starts Jetty
    config.clj      env vars merged over backend/.env (KEY=value or KEY: value)
    db.clj          Store protocol — handlers only depend on this
    images.clj      ImageStore protocol; images/s3.clj (AWS SDK v2, path-style), images/memory.clj (tests)
    db/sql.clj      Store impl for SQLite and PostgreSQL (next.jdbc + honeysql, Hikari pool for PG), picked by DB_URL, creates tables on start
    schema.clj      malli schemas (coercion + OpenAPI)
    api/*.clj       handlers + route data per resource (public routes, /api/admin/*)
  dev/user.clj      REPL helpers: (start) (stop) (restart)
  test/auksjon/     clojure.test, run via cognitect test-runner
.devcontainer/      dev container: JDK 21 + Clojure CLI, Node 22, scw CLI, jq, host Docker
deploy/             Scaleway Serverless Containers + Serverless SQL (scw CLI scripts)
frontend/           TypeScript + Vite (multi-page build)
  index.html        public catalog entry -> src/main.ts (plain DOM)
  admin/index.html  admin entry -> src/admin/main.tsx (React SPA, basename /admin)
  list/index.html   printable catalog table -> src/list.ts (plain DOM, /list/#<catalogId>; all catalogs when logged in as admin)
  src/api.ts        typed wrappers around fetch("/api/..."), shared by both apps
  src/admin/        React 19 + React Router + TanStack Query + Tailwind 4 + dnd-kit
    queries.ts      query/mutation hooks + cache keys (all server state goes through here)
    components/     ui.tsx primitives (Button, Card, Field, …) and composite widgets
    pages/          one component per route
  vite.config.ts    dev server on :5173, proxies /api -> localhost:3000, /admin/* SPA fallback
```

## Commands

Backend (run from `backend/`):
- `clojure -M:run` — start server on :3000 (env or `.env`: `PORT`, `DB_URL`, `STATIC_DIR` default `../frontend/dist`, `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_WEB_URL`, `DB_USER`, `DB_PASSWORD` (Postgres), `ADMIN_USER`, `ADMIN_PASSWORD_HASH`). Swagger UI: `/api/docs`
- `clojure -M:hash-password` — prompt for a password, print `ADMIN_PASSWORD_HASH=pbkdf2-sha256$…` for `.env`
- `clojure -X:test` — run tests (SQLite; set `TEST_DB_URL=jdbc:postgresql://…?user=…&password=…` to run against Postgres — it truncates the tables)
- `clojure -M:dev -r` or jack in with the `:dev` alias, then `(start)` — REPL-driven dev with reload on re-eval

Frontend (run from `frontend/`):
- `npm install` — once
- `npm run dev` — Vite dev server on :5173 with /api proxied to the backend
- `npm run build` — typecheck + production build to `frontend/dist/`
- `npm run typecheck` — tsc only

Container: `docker build -t auksjon .` at repo root (multi-stage: Vite build, `clojure -T:build uber` AOT uberjar via `backend/build.clj`, temurin 21 JRE). Run with `-p 3000:3000 -v <vol>:/data --env-file …`; SQLite lives in `/data`, heap capped at 128m via `JAVA_TOOL_OPTIONS`.

Deploy (Scaleway, `scw` CLI configured with `scw init`): settings in `deploy/config.env`, secrets in `deploy/secrets.env` (gitignored, template `secrets.env.example`).
- `deploy/setup.sh` — once, idempotent: registry namespace, Serverless SQL DB, container namespace (IAM app + key managed by hand; `DB_USER` = application id, `DB_PASSWORD` = its secret key)
- `deploy/deploy.sh [tag]` — build (linux/amd64), push `<registry>/auksjon:<git-sha>`, create/update the container with env + secrets, attach `DOMAIN` if set; with a tag, redeploys that pushed image (rollback)

Production-style run: `npm run build` in `frontend/`, then `clojure -M:run` in `backend/`; the backend serves `frontend/dist` at `/` (with `/admin/*` falling back to `admin/index.html`) and the API at `/api`.

## Conventions

- API: JSON only, all endpoints under `/api`. Handlers are plain `request -> response` fns returning Clojure data as `:body`; muuntaja encodes it. Add routes in `auksjon.handler/routes`.
- Auth: `POST /api/login` `{username, password}` sets an HttpOnly `auksjon_session` cookie (Path=/api, SameSite=Strict, 30 days; stateless HMAC token keyed from `ADMIN_PASSWORD_HASH`, so changing the password logs everyone out). Everything under `/api/admin` requires it (`auth/wrap-admin-auth`, outermost middleware, before body parsing) and returns plain 401 otherwise. `POST /api/logout` clears it, `GET /api/admin/session` returns `{username}`. Admin app: `AuthGate` shows `LoginPage` when not logged in; any 401 drops back to it. Tests wrap the app in `test-util/authed`.
- Images (Scaleway Object Storage): admin app calls `POST /api/admin/images/upload-form` `{contentType, size}` → `{uploadUrl, fields, url}` (SigV4 POST policy from `images/post_policy.clj`: exact key, content type, size ≤ 25 MB, `acl=public-read`, 10 min expiry), then POSTs `fields` + `file` directly to S3 (204). `url` (`<S3_WEB_URL>/<key>`) goes in `item.images`. `POST /api/admin/images` (multipart via backend) remains as fallback. Browsers load images directly from `S3_WEB_URL` (unauthenticated). Bucket needs CORS allowing POST from the app origin: `clojure -X auksjon.images.s3/configure-cors! :origins '["https://…"]'` (needs a key with bucket-config permission).
- Every new endpoint gets a test in `backend/test` using `ring.mock.request` against `(test-util/authed (handler/app {:store (test-util/fresh-store) :auth test-util/test-auth}))`.
- JSON keys are camelCase (`priceLow`, `mainImage`); request schemas are closed maps (unknown keys → 400).
- Frontend: keep API calls in `src/api.ts` with explicit response interfaces that mirror the backend's JSON. Strict TypeScript; don't use `any`.
- Admin app: React function components, Tailwind utility classes (reuse `components/ui.tsx` primitives), server state only via TanStack Query hooks in `queries.ts`. UI text in Norwegian (bokmål).
- Public app: plain DOM, no framework. No extra libraries without a reason.
- Both servers bind `0.0.0.0` so they're reachable when ports are published from the sandbox.

## Before finishing a change

- `clojure -X:test` passes in `backend/`
- `npm run build` passes in `frontend/`

## External documentation

This will run on scaleway cloud services. Some neccessary documentation is available here:

- Object storage is S3, but with some variations and limitations. The API is described here: https://www.scaleway.com/en/docs/object-storage/api-cli/object-operations.md
- Using POST for uploading to S3 directly from browser: https://www.scaleway.com/en/docs/object-storage/api-cli/post-object.md
