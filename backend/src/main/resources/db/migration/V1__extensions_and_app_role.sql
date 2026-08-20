-- Required for the GiST exclusion constraint on (doctor_id, appointment_range)
-- in V2: GiST-indexing a plain equality column (doctor_id) needs btree_gist.
-- See docs/designs/jdwnrh-scheduler.md, "booking-safety mechanism".
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Non-owner application role. Postgres table owners bypass RLS policies by
-- default, so the app must NEVER connect as the migrating/owning role in
-- production. This migration creates the role; the app's runtime datasource
-- (see application.yml, APP_DB_USERNAME) connects as this role.
--
-- NOTE: CREATE ROLE requires the migrating role to have CREATEROLE (or be a
-- superuser). If your Neon connection role lacks that privilege, run this
-- block manually once via the Neon SQL console/psql as the project's owner
-- role, then let Flyway continue from V2 (mark V1 as already-applied, or run
-- with -outOfOrder / baseline as appropriate for your Flyway setup).
-- Password comes from a Flyway placeholder (spring.flyway.placeholders.schedulerAppPassword,
-- sourced from the APP_DB_PASSWORD env var — see application.yml) so the real
-- secret is never committed to source control. Flyway substitutes ${...}
-- before sending this SQL; it is not psql/native Postgres syntax.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scheduler_app') THEN
        EXECUTE format('CREATE ROLE scheduler_app LOGIN PASSWORD %L', '${schedulerAppPassword}');
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO scheduler_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO scheduler_app;

-- Default privileges so tables created by later migrations (still run as the
-- owning role) are automatically usable by scheduler_app without a manual
-- GRANT in every subsequent migration.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO scheduler_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO scheduler_app;
