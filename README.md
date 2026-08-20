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

Walking-skeleton milestone (patient role, one department, full booking-correctness core) is done, plus step 7's build-out: reference-data admin, staff/doctor daily workflows, the frontend for all of it, real notification sending and appointment reminders (step 8), and — as of this round — a general audit log (MVP Scope's "Admin ... audit logs" item). **Verified against a real local PostgreSQL 18 database** end-to-end via `curl` (backend) plus `tsc -b` and `vite build` (frontend) — not just compiled: admin creates a department → specialty → appointment type → doctor account → weekly schedule block; that data is immediately visible on the public browsing endpoints; a patient books the real generated slot; front-desk staff check the patient in and move them to waiting; the doctor starts and completes the consultation. No browser-automation tool was available in this environment, so the new pages below were verified by exact request/response-shape matching against the live API and a clean production build, not by clicking through them in an actual browser — worth a manual pass before you trust it fully.

What's new since the walking skeleton:
- **Admin CRUD** (`/api/admin/**`, gated to `DEPARTMENT_ADMIN`/`HOSPITAL_ADMIN`/`SUPER_ADMIN`): departments, specialties, appointment types, holidays, staff/doctor account provisioning, and per-doctor weekly schedule + leave/blocked-period management. Department/specialty/appointment-type/doctor tables are deliberately not RLS-protected (public reference data, same as before) — a new `AdminScope` component is the application-layer gate that keeps a department admin inside their own department; `app_user` (which *is* RLS-protected) got two new policies (`V14`) so a department admin can provision and list their own department's staff without escalating to a hospital admin every time.
- **Staff daily queue** (`/api/staff/**`): check-in, move-to-waiting, no-show — scoped to the caller's department by the existing `appointment_department_scoped_staff` RLS policy, no new policy needed.
- **Doctor portal** (`/api/doctor-portal/**`): the doctor's own day, start/complete consultation, and self-service leave management — a doctor can mark their own blocked time without an admin in the loop (`schedule_exception_doctor_own`, also `V14`).
- `doctor_schedule` gained `UPDATE`/`DELETE` RLS policies (only `SELECT`+`INSERT` existed before — there was no way to edit or remove a schedule block); `schedule_exception` went from **no RLS at all** to public-read/gated-write, matching the precedent `V9` set for `doctor_schedule`.
- `/api/auth/me` — the frontend had no way to know the logged-in user's role/department (only whether a token existed), which blocks any role-based routing. Carved out of the otherwise-`permitAll` `/api/auth/**` prefix in `SecurityConfig`.
- `get_visible_patient_names()` (`V15`) — a narrow SECURITY DEFINER function, same shape as `V12`'s doctor-profile lookup, so the staff queue and doctor portal can show a patient's *name* instead of a bare UUID. Its `WHERE` clause is a direct copy of the three role branches already in `V3`'s appointment RLS policies, so it structurally cannot expose a name that appointment RLS wouldn't already let the caller see the appointment for.
- **Frontend now covers every role**, not just patients: role-aware nav and post-login routing (`lib/roles.ts`, `ProtectedRoute`'s new `roles` prop), a front-desk queue page, a doctor portal (day view + consultation actions + own schedule/leave), and three admin pages (departments/specialties/appointment-types/holidays, staff directory + account creation, per-doctor schedule/leave management).
- **Signup now offers a role picker** (Patient / Doctor / Nurse / Receptionist, each with a short description of what that role can actually do) — but picking anything other than Patient does **not** create an active account. It submits a request (`staff_access_request`, `V16`) that an admin has to approve from the new "Requests" admin page before the person can log in. This was a deliberate call, not an oversight: letting signup grant a role directly would let anyone self-grant `DOCTOR` or `HOSPITAL_ADMIN` and immediately see patient data, which directly undoes the CEO-review decision recorded in `V7` that staff/doctor/admin accounts are hospital-issued, not public signup. `DEPARTMENT_ADMIN` and above stay admin-console-only — not requestable through this flow at all. The request table holds a bcrypt hash (same as `app_user`, never plaintext) so approval doesn't need to ask for the password again; the hash is cleared once the request is reviewed either way, and RLS scopes who can even see a pending request (department admin: their own department; hospital/super admin: everything) the same way `V14` scoped `app_user`.
- **Notifications actually send now.** Booking/cancel/reschedule have written `notification_outbox` rows since the walking skeleton, but nothing ever consumed them — `NotificationSenderJob` now polls PENDING rows every 15s (`FOR UPDATE SKIP LOCKED`, so multiple backend instances never double-send), renders a template per event type in Asia/Thimphu (never raw UTC), and delivers via `EmailSender`. Two implementations: `LoggingEmailSender` (default — this is a portfolio build with no real SMTP account, so "sending" means logging the fully-rendered email visibly) and `SmtpEmailSender` (real delivery, `APP_MAIL_ENABLED=true` + standard `spring.mail.*` properties). A failed send backs off exponentially (20s, 40s, 80s...) and gives up after 5 attempts rather than either hammering a down provider or retrying forever.
- **24h/2h appointment reminders** (`ReminderJob`, every 60s) — claims candidates via two new SECURITY DEFINER functions (`claim_24h_appointment_reminders` / `claim_2h_appointment_reminders`, `V18`) that atomically claim-and-mark (`UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)`) so two concurrently-running instances can never send the same reminder twice — exactly the distributed-lock requirement the design doc calls out for this job. Reuses the same outbox + sender path as everything else; verified by fast-forwarding a real appointment's `start_time` and watching both windows fire exactly once each.
- **General audit log** — the `audit_log` table has existed since `V2`, but nothing had ever written to it (appointment status changes have their own dedicated `appointment_history` trail; this is the broader "who changed what admin setting, when" log the MVP scope calls for separately). `AuditLogger` now records every admin-console mutation — department/specialty/appointment-type/holiday CRUD, staff account creation, doctor bio/schedule/leave changes, access-request approve/reject — with before/after JSON, in the *same transaction* as the mutation it records (so a rolled-back change never leaves a phantom audit entry). Read side (`/api/admin/audit-log`, new "Audit Log" admin page) is deliberately `HOSPITAL_ADMIN`/`SUPER_ADMIN` only, not department-scoped like the rest of `/api/admin/**` — this is hospital-wide governance data.

Seven real bugs were only findable by actually running this against Postgres, not by reading the code — all fixed:
- Every entity's `createdAt`/`updatedAt` fields were never set before insert, so Hibernate sent an explicit `NULL`, silently overriding the DB's `DEFAULT now()` and violating the `NOT NULL` constraint (`V1`-era design, fixed by threading the injected `Clock` through every entity constructor — including, this round, `Department`/`Specialty`/`AppointmentType`/`Doctor`/`DoctorSchedule`/`Holiday`, which had never been created via JPA before now).
- A classic RLS gotcha (`V13`): `current_setting('app.current_department_id', true)::uuid` throws on an empty string, which is what a non-department-scoped role (e.g. PATIENT) gets set to — and SQL doesn't reliably short-circuit `AND`, so the cast could fire even when a sibling role-check condition should have excluded the row. Fixed with `NULLIF(..., '')` before every such cast (and applied again in `V14` for the two new `app_user` policies).
- `RlsSessionInitializer.applyCurrentContext()` requires `Propagation.MANDATORY` — it must be called from inside an existing `@Transactional` method, never straight from a controller. First draft of `AdminUserController` broke that rule and every admin-staff request failed with a 403 masking an `IllegalTransactionStateException` underneath; fixed by moving the DB-touching logic into a proper `@Transactional` `AdminUserService`, matching the pattern every other RLS-touching call in this codebase already follows.
- Postgres's `SET LOCAL` session variables (what every RLS policy and `get_visible_patient_names()` reads) don't survive past the transaction that set them — so the enriched staff/doctor daily views can't call `applyCurrentContext()` in one `@Transactional` method and then query patient names in a second, separate one; they have to happen inside the *same* transaction (`AppointmentQueryService.listForDayWithPatientNames`).
- `<input type="datetime-local">` carries no timezone of its own — the browser hands back a bare wall-clock string that `new Date(...)` would otherwise interpret in the *viewer's* local timezone, not the hospital's. Bhutan Time is a fixed UTC+6 with no DST, so `lib/formatting.ts`'s `thimphuWallTimeToInstant()` appends `+06:00` explicitly rather than trusting the browser's guess — used by both the doctor's own leave form and the admin's per-doctor leave form.
- `StaffAccessRequest.approve()`/`.reject()` deliberately null out the stored password hash once it's no longer needed — but `V16` declared that column `NOT NULL`, so the very first approval threw a constraint violation. Fixed forward with `V17` (the table was already live in the dev DB by the time this was caught) rather than editing `V16` in place.
- Throwing a plain `ResponseStatusException` from `StaffAccessRequestService` produced a bare, unexplained `403` with an empty body — for reasons not fully root-caused (something about this Spring Boot 4.1 / Spring Security 7 combination), not the `409` its status code says. Every other controller in this codebase already uses a custom exception + a local `@ExceptionHandler` instead of `ResponseStatusException`, and switching to that proven pattern (`AccessRequestAlreadyReviewedException`) fixed it immediately — a reminder that this codebase's established patterns exist for a reason, not just style.
- **Reschedule was broken every single time**, not just on rare collisions: `AppointmentLifecycleService.reschedule()` transitions the old appointment to `RESCHEDULED` (a plain `save()`, not flushed) and then inserts a new row carrying the *same* reference number, immediately followed by `saveAndFlush()`. Hibernate's default flush order runs ALL pending inserts before ALL pending updates within one flush, regardless of call order — so the new row's insert executed while the old row's status-change update hadn't hit the database yet, tripping `uq_appointment_reference_active` (two "active" rows, same reference number) on every call. Fixed by forcing the old row's update to flush first (`saveAndFlush(original)`). This is the first time reschedule had actually been exercised against a real Postgres database in this project.
- `ReferenceNumberGenerator`'s own javadoc claimed `BookingService` "retries on the rare unique-index collision" — it didn't; only the unrelated exclusion-constraint case was handled. Fixing it properly surfaced a second problem: a naive retry-after-failure can't work at all here, because Postgres aborts the *entire transaction* on any statement error until rollback, so a second `saveAndFlush` attempt in the same `@Transactional` method would just fail immediately with "current transaction is aborted." Fixed with a check *before* the insert instead — which needed its own SECURITY DEFINER function (`reference_number_active_exists`, `V19`), because the booking caller is always a `PATIENT` and `appointment`'s RLS only lets a patient see their *own* rows, so a plain RLS-scoped existence check would have silently checked nothing useful.

Not yet built: admin reporting. Not yet run: the concurrent-booking load test (needs Docker/Testcontainers, not installed on this dev machine); a real click-through of the frontend in an actual browser (no browser-automation tool in this environment — verified via `tsc -b`/`vite build` plus exact request/response-shape matching against the live API instead). Nothing has been deployed. See "Next Steps" in the design doc for the full build order.
