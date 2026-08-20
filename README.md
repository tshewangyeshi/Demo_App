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

- **Flyway** runs migrations as the schema-owning role (`FLYWAY_DB_USERNAME`/`FLYWAY_DB_PASSWORD`) — locally, that's your Postgres superuser (`postgres`).
- **The app** connects at runtime as `scheduler_app`, a non-owner role created by `V1__extensions_and_app_role.sql`, with `FORCE ROW LEVEL SECURITY` in effect on RLS-protected tables (`V3__row_level_security.sql`).

Against Neon (or any real deployment), both must point at the **direct** connection string, never a pgbouncer-pooled one — RLS's `SET LOCAL` session variables don't survive pooled connections in transaction mode. Locally there's no pooler in the picture at all, so this only matters once you deploy.

**scheduler_app's password is deliberately not set by a migration** (templating secrets into checked-in SQL is fragile — see the comment at the top of `V1__extensions_and_app_role.sql`). `V1` creates the role with an unusable placeholder password; you set the real one yourself, once, right after migrating.

#### Local setup (verified working against PostgreSQL 18)

1. **Create the database** (as your Postgres superuser):
   ```bash
   psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE scheduler;"
   ```
2. **Set env vars and start the app once** — this runs all migrations (including creating the `scheduler_app` role) via Spring Boot's own Flyway integration on startup:
   ```bash
   export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"   # see JDK note above
   export FLYWAY_DB_URL="jdbc:postgresql://127.0.0.1:5432/scheduler"
   export FLYWAY_DB_USERNAME="postgres"
   export FLYWAY_DB_PASSWORD="<your postgres superuser password>"
   export APP_DB_URL="jdbc:postgresql://127.0.0.1:5432/scheduler"
   export APP_DB_PASSWORD="<pick a strong password for scheduler_app>"
   cd backend
   ./mvnw spring-boot:run
   ```
   First run will fail once the app tries to connect as `scheduler_app` — expected, since that role still has the placeholder password. Stop it (Ctrl+C).
3. **Set the real `scheduler_app` password** (one-time, as the superuser):
   ```bash
   psql -U postgres -h 127.0.0.1 -c "ALTER ROLE scheduler_app PASSWORD '<the same APP_DB_PASSWORD you set above>';"
   ```
4. **Start the app again** with the same env vars — it should boot cleanly and serve on `:8080`.

Keep `FLYWAY_DB_PASSWORD`/`APP_DB_PASSWORD` as env vars in your shell (or a local, gitignored script) — never commit them. `APP_DB_USERNAME` defaults to `scheduler_app`, no need to set it.

#### Against Neon / a real deployment

Same env vars, pointed at Neon's direct connection string instead:
```bash
export FLYWAY_DB_URL="jdbc:postgresql://<neon-direct-host>/scheduler"
export FLYWAY_DB_USERNAME="<neon-owner-role>"
export FLYWAY_DB_PASSWORD="<neon-owner-password>"
export APP_DB_URL="jdbc:postgresql://<neon-direct-host>/scheduler"
export APP_DB_PASSWORD="<a-new-strong-password-for-scheduler_app>"
```
If your Neon connection role lacks `CREATEROLE`, `V1`'s `CREATE ROLE` step will fail — run that one block manually via the Neon SQL console as the project owner, then let Flyway continue with V2 onward. Same "set the real password after V1 runs" step applies.

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

Walking-skeleton milestone: patient role, one department, full booking-correctness core, end-to-end from the UI down to the exclusion constraint. **Verified against a real local PostgreSQL 18 database** — registration, login, and RLS-scoped reads all confirmed working end-to-end (`curl` smoke tests, not just compiled). Frontend builds clean and was checked in a real browser, including its error-handling path.

Two real bugs were only findable by actually running this against Postgres, not by reading the code — both fixed:
- Every entity's `createdAt`/`updatedAt` fields were never set before insert, so Hibernate sent an explicit `NULL`, silently overriding the DB's `DEFAULT now()` and violating the `NOT NULL` constraint (`V1`-era design, fixed by threading the injected `Clock` through every entity constructor).
- A classic RLS gotcha (`V13`): `current_setting('app.current_department_id', true)::uuid` throws on an empty string, which is what a non-department-scoped role (e.g. PATIENT) gets set to — and SQL doesn't reliably short-circuit `AND`, so the cast could fire even when a sibling role-check condition should have excluded the row. Fixed with `NULLIF(..., '')` before every such cast.

Not yet run: the concurrent-booking load test itself (needs Docker/Testcontainers for the RLS+exclusion-constraint integration tests, not installed on this dev machine), and nothing has been deployed. See "Next Steps" in the design doc for the build order from here.
