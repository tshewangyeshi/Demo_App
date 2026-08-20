-- Public role requests: patients still get instant self-registration (V7),
-- but requesting staff/doctor access now goes through review instead of
-- either (a) being blocked entirely with no path for a prospective hire to
-- even express interest, or (b) self-granting a role outright, which would
-- undo the CEO-review decision that staff/doctor/admin accounts are
-- hospital-issued, not public signup (see V7's comment).
--
-- Deliberately narrower than app_user_admin_write's role set: only
-- DOCTOR/NURSE/RECEPTIONIST are requestable here. DEPARTMENT_ADMIN and
-- above stay admin-console-only (see AdminUserController) — those are
-- appointed, not requested through a public form.
CREATE TABLE staff_access_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               TEXT NOT NULL,
    password_hash       TEXT NOT NULL, -- hashed at submission time, same as app_user — never stored/handled as plaintext
    requested_role      TEXT NOT NULL CHECK (requested_role IN ('DOCTOR','NURSE','RECEPTIONIST')),
    department_id       UUID NOT NULL REFERENCES department(id),
    first_name          TEXT NOT NULL,
    last_name           TEXT NOT NULL,
    bio                 TEXT,
    status              TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    reviewed_by         UUID REFERENCES app_user(id),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_access_request_status ON staff_access_request (status);

ALTER TABLE staff_access_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_access_request FORCE ROW LEVEL SECURITY;

-- Anyone can submit a request — same public-INSERT shape as V7's patient
-- self-registration policy (no session context exists yet at signup time).
CREATE POLICY staff_access_request_public_submit ON staff_access_request
    FOR INSERT
    WITH CHECK (true);

-- Only admins can see or act on requests (they carry a name, email, and
-- password hash for someone who isn't an authenticated user yet) — hospital/
-- super admin see everything, a department admin only their own
-- department's requests, matching the app_user_department_admin_* policies
-- from V14.
CREATE POLICY staff_access_request_admin_manage ON staff_access_request
    FOR ALL
    USING (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR (
            current_setting('app.current_role', true) = 'DEPARTMENT_ADMIN'
            AND department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
        )
    )
    WITH CHECK (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR (
            current_setting('app.current_role', true) = 'DEPARTMENT_ADMIN'
            AND department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
        )
    );
