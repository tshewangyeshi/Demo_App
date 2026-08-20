package bt.gov.jdwnrh.scheduler.iam;

/**
 * Fixed, compile-time enum for v1 — see docs/designs/jdwnrh-scheduler.md,
 * "MVP Scope": roles/permissions are not a dynamic, admin-editable set.
 * SUPER_ADMIN's "role/permission management" means assigning one of these
 * existing roles to a user, not building a permission editor.
 */
public enum Role {
    PATIENT,
    DOCTOR,
    NURSE,
    RECEPTIONIST,
    DEPARTMENT_ADMIN,
    HOSPITAL_ADMIN,
    SUPER_ADMIN
}
