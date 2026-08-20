-- StaffAccessRequest.approve()/reject() deliberately clears password_hash
-- once it's no longer needed (the real app_user row holds its own hash by
-- then) — V16 mistakenly declared this column NOT NULL, so that exact
-- clear-on-review step failed with a constraint violation. Only found by
-- actually running the approval flow end-to-end, not by reading the code.
ALTER TABLE staff_access_request ALTER COLUMN password_hash DROP NOT NULL;
