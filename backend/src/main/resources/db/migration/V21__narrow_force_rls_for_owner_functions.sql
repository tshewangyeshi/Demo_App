-- Found deploying to Render: every SECURITY DEFINER function in this app
-- (V6/V8/V10/V12/V15/V18/V19/V20) relies on running with the OWNER's
-- privileges to bypass RLS for one narrow, already-scoped query — exactly
-- as each of those migrations' own comments describe. That worked locally
-- purely by coincidence: the local FLYWAY_DB_USERNAME role (postgres) is an
-- actual Postgres superuser, and superusers always bypass RLS regardless of
-- FORCE ROW LEVEL SECURITY. On any real managed Postgres (Render, and this
-- would equally hit Neon — the originally intended production target,
-- whose "owner" roles aren't true superusers either), the owner role is
-- NOT a superuser, so FORCE ROW LEVEL SECURITY applies to it too (per
-- Postgres's own docs: "a table owner can choose to be subject to row
-- security... [via] FORCE ROW LEVEL SECURITY" — and once chosen, only
-- superusers or roles with BYPASSRLS remain exempt). Every one of those
-- SECURITY DEFINER functions was silently returning zero rows in
-- production — including login itself (V6), discovered when a real
-- deployed login attempt failed with a genuine 401 despite correct
-- credentials.
--
-- Fix: NO FORCE ROW LEVEL SECURITY on every table these functions touch.
-- This does NOT weaken scheduler_app's RLS — scheduler_app is a non-owner
-- role, and non-owner roles are ALWAYS subject to RLS regardless of FORCE
-- (FORCE only ever changed whether the OWNER itself was exempt). The owner
-- role (FLYWAY_DB_USERNAME) is never used to handle live application
-- traffic — only migrations and these narrow, already-scoped functions run
-- as it — so this restores the exact behavior local dev always had
-- (owner exempt from RLS), it doesn't grant anything new in practice.
ALTER TABLE app_user NO FORCE ROW LEVEL SECURITY;
ALTER TABLE appointment NO FORCE ROW LEVEL SECURITY;
ALTER TABLE doctor_schedule NO FORCE ROW LEVEL SECURITY;
ALTER TABLE appointment_history NO FORCE ROW LEVEL SECURITY;
ALTER TABLE schedule_exception NO FORCE ROW LEVEL SECURITY;
ALTER TABLE staff_access_request NO FORCE ROW LEVEL SECURITY;
