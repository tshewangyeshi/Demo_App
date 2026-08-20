# JDWNRH Hospital Appointment Scheduling System

Scenario/portfolio build (not a real deployment — see `docs/designs/jdwnrh-scheduler.md`, Premise 2). Full design, review history, and architecture decisions live in [`docs/designs/jdwnrh-scheduler.md`](docs/designs/jdwnrh-scheduler.md).

## Backend

Java 21 (target) / Spring Boot 4.1, Maven (wrapper included, no local Maven install needed).

### One-time local setup

This machine has two JDKs installed; `JAVA_HOME` defaults to JDK 17, which cannot build a `java.version=21` target. Override it before running `mvnw`:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"   # adjust path if different on your machine
```

### Database

Two Postgres roles by design (see the design doc's "RLS only binds if the app connects as a non-owner role"):

- **Flyway** runs migrations as the schema-owning role (`FLYWAY_DB_USERNAME`/`FLYWAY_DB_PASSWORD`, default `postgres`).
- **The app** connects at runtime as `scheduler_app`, a non-owner role created by `V1__extensions_and_app_role.sql`, with `FORCE ROW LEVEL SECURITY` in effect on RLS-protected tables (`V3__row_level_security.sql`).

Both must point at Neon's **direct** connection string, never the pgbouncer-pooled one — RLS's `SET LOCAL` session variables don't survive pooled connections in transaction mode.

Required environment variables:

```bash
export FLYWAY_DB_URL="jdbc:postgresql://<neon-direct-host>/scheduler"
export FLYWAY_DB_USERNAME="<neon-owner-role>"
export FLYWAY_DB_PASSWORD="<neon-owner-password>"
export APP_DB_URL="jdbc:postgresql://<neon-direct-host>/scheduler"
export APP_DB_PASSWORD="<a-new-strong-password-for-scheduler_app>"
```

`APP_DB_USERNAME` defaults to `scheduler_app` — no need to set it unless you rename the role.

**First run:** if your Neon connection role lacks `CREATEROLE`, `V1__extensions_and_app_role.sql`'s `CREATE ROLE` step will fail — run that one block manually via the Neon SQL console as the project owner, then let Flyway continue with V2 onward.

### Build & run

```bash
cd backend
./mvnw compile   # or test, or spring-boot:run
```

## Frontend

React + Vite + TypeScript SPA, calling the backend REST API directly.

```bash
cd frontend
npm install
npm run dev   # starts on http://localhost:5173, proxies /api to http://localhost:8080
```

The dev server proxies `/api/*` to the backend (see `vite.config.ts`) so requests are same-origin during local development — no CORS/cookie setup needed to run both locally. A production build talking to a deployed backend on a different origin relies on the backend's CORS config (`APP_CORS_ALLOWED_ORIGIN`, see `SecurityConfig`) and the refresh cookie's `SameSite=None` — both already wired for a genuinely cross-origin deployment (Vercel frontend + Railway/Render backend).

The access token lives in memory only (never localStorage) — see `src/api/tokenStore.ts`. A page reload always re-derives it from the httpOnly refresh cookie via `/api/auth/refresh`.

## Status

Walking-skeleton milestone: patient role, one department, full booking-correctness core, end-to-end from the UI down to the exclusion constraint. Backend compiles and the frontend builds clean, but neither has been run against a real database yet — no Docker on the dev machine (blocks Testcontainers) and no Neon project connected yet. See "Next Steps" in the design doc for the build order from here.
