# Auksjon

Web app with a Clojure backend (JSON API) and a TypeScript/HTML frontend (Vite, no framework).

The app is specified in @SPEC.md

Keep your replies extremely concise and focus on conveying the key information. The user is an expert in clojure and only needs a terse summary of responses.

## Layout

```
backend/            Clojure (tools.deps)
  deps.edn          deps + aliases (:run, :dev, :test)
  src/auksjon/
    handler.clj     reitit router, middleware, coercion, swagger; all routes under /api
    main.clj        -main, builds store (env DB_URL, default jdbc:sqlite:auksjon.db), starts Jetty
    db.clj          Store protocol — handlers only depend on this
    db/sqlite.clj   SQLite impl (next.jdbc + honeysql), creates tables on start
    schema.clj      malli schemas (coercion + OpenAPI)
    api/*.clj       handlers + route data per resource (public routes, /api/admin/*)
  dev/user.clj      REPL helpers: (start) (stop) (restart)
  test/auksjon/     clojure.test, run via cognitect test-runner
frontend/           TypeScript + Vite
  index.html        entry page
  src/main.ts       entry script
  src/api.ts        typed wrappers around fetch("/api/...")
  vite.config.ts    dev server on :5173, proxies /api -> localhost:3000
```

## Commands

Backend (run from `backend/`):
- `clojure -M:run` — start server on :3000 (env: `PORT`, `DB_URL`, `STATIC_DIR` default `../frontend/dist`). Swagger UI: `/api/docs`
- `clojure -X:test` — run tests
- `clojure -M:dev -r` or jack in with the `:dev` alias, then `(start)` — REPL-driven dev with reload on re-eval

Frontend (run from `frontend/`):
- `npm install` — once
- `npm run dev` — Vite dev server on :5173 with /api proxied to the backend
- `npm run build` — typecheck + production build to `frontend/dist/`
- `npm run typecheck` — tsc only

Production-style run: `npm run build` in `frontend/`, then `clojure -M:run` in `backend/`; the backend serves `frontend/dist` at `/` and the API at `/api`.

## Conventions

- API: JSON only, all endpoints under `/api`. Handlers are plain `request -> response` fns returning Clojure data as `:body`; muuntaja encodes it. Add routes in `auksjon.handler/routes`.
- Every new endpoint gets a test in `backend/test` using `ring.mock.request` against `(handler/app {:store (test-util/fresh-store)})`.
- JSON keys are camelCase (`priceLow`, `mainImage`); request schemas are closed maps (unknown keys → 400).
- Frontend: keep API calls in `src/api.ts` with explicit response interfaces that mirror the backend's JSON. Strict TypeScript; don't use `any`.
- No frontend framework or extra libraries without a reason — prefer plain DOM APIs.
- Both servers bind `0.0.0.0` so they're reachable when ports are published from the sandbox.

## Before finishing a change

- `clojure -X:test` passes in `backend/`
- `npm run build` passes in `frontend/`
