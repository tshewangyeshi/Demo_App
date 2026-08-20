-- Admin CRUD (departments/specialties/appointment types/holidays/doctors/
-- schedules/exceptions) is landing on top of this migration. Two gaps this
-- closes:
--
-- 1. doctor_schedule only had SELECT + INSERT policies (V3/V9) — there was
--    no way to edit or remove a schedule block once created.
-- 2. schedule_exception had NO row level security at all. That was fine
--    while nothing wrote to it, but now that admin/doctor endpoints can
--    create leave/blocked periods, writes need to be gated at the DB level
--    like everything else in this schema.

CREATE POLICY doctor_schedule_update_scoped ON doctor_schedule
    FOR UPDATE
    USING (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN'))
    WITH CHECK (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN'));

CREATE POLICY doctor_schedule_delete_scoped ON doctor_schedule
    FOR DELETE
    USING (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN'));

ALTER TABLE schedule_exception ENABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_exception FORCE ROW LEVEL SECURITY;

-- Same precedent as V9's doctor_schedule_public_read: a leave/blocked-period
-- window isn't sensitive the way an individual appointment row is, and the
-- public /api/availability endpoint (SlotGenerationService) reads this table
-- with NO RlsContext applied at all (anonymous patients browsing) — it needs
-- every doctor's exceptions to compute correct slots.
CREATE POLICY schedule_exception_public_read ON schedule_exception
    FOR SELECT
    USING (true);

CREATE POLICY schedule_exception_admin_write ON schedule_exception
    FOR ALL
    USING (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN'))
    WITH CHECK (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN'));

-- A doctor can mark their own leave/blocked time directly, without needing
-- an admin in the loop for something like "I'm out sick tomorrow".
CREATE POLICY schedule_exception_doctor_own ON schedule_exception
    FOR ALL
    USING (
        current_setting('app.current_role', true) = 'DOCTOR'
        AND doctor_id IN (
            SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid
        )
    )
    WITH CHECK (
        current_setting('app.current_role', true) = 'DOCTOR'
        AND doctor_id IN (
            SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid
        )
    );

-- Staff provisioning: V3's app_user_admin_write (INSERT/UPDATE/DELETE) is
-- HOSPITAL_ADMIN/SUPER_ADMIN only; V7's self-registration is PATIENT only.
-- That leaves no way for a department admin to do routine staffing changes
-- (hiring a nurse, onboarding a doctor) without escalating to a hospital
-- admin every time — this is the missing middle, scoped narrowly: a
-- department admin may only create DOCTOR/NURSE/RECEPTIONIST accounts
-- (never another admin account) inside their own department.
CREATE POLICY app_user_department_admin_provision ON app_user
    FOR INSERT
    WITH CHECK (
        current_setting('app.current_role', true) = 'DEPARTMENT_ADMIN'
        AND role IN ('DOCTOR', 'NURSE', 'RECEPTIONIST')
        AND department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
    );

-- Same gap on the read side: app_user_self_or_admin (V3) only lets a caller
-- see their own row or (if HOSPITAL_ADMIN/SUPER_ADMIN) every row. A
-- department admin managing their own staff roster needs to list them.
-- Patients have a NULL department_id (see V2), so this naturally never
-- exposes patient rows to a department admin.
CREATE POLICY app_user_department_admin_view ON app_user
    FOR SELECT
    USING (
        current_setting('app.current_role', true) = 'DEPARTMENT_ADMIN'
        AND department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
    );
