package bt.gov.jdwnrh.scheduler.admin;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * The application-layer authorization check for reference data (department/
 * specialty/appointment_type/holiday/doctor) — these tables are deliberately
 * NOT RLS-protected (see V3's closing comment: they're public browsing data),
 * so unlike Appointment/DoctorSchedule, nothing in Postgres stops a
 * DEPARTMENT_ADMIN from writing another department's rows. This class is
 * that missing gate, checked at the top of every /api/admin/** write.
 *
 * SecurityConfig's path-prefix rule already keeps non-admin roles out of
 * /api/admin/** entirely; this narrows further, to "which department".
 */
@Component
public class AdminScope {

    private final CurrentUser currentUser;

    public AdminScope(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public RlsContext require() {
        return currentUser.require();
    }

    public boolean isHospitalWide(RlsContext caller) {
        return caller.role() == Role.HOSPITAL_ADMIN || caller.role() == Role.SUPER_ADMIN;
    }

    /** For hospital-wide resources (creating a department itself, a hospital-wide holiday, a staff account outside any single department). */
    public void requireHospitalWide(RlsContext caller) {
        if (!isHospitalWide(caller)) {
            throw new AccessDeniedException("Only a hospital/super admin can do this");
        }
    }

    /** For department-scoped resources: hospital/super admin can touch any department; a department admin only their own. */
    public void requireDepartmentAccess(RlsContext caller, UUID departmentId) {
        if (isHospitalWide(caller)) {
            return;
        }
        if (caller.role() == Role.DEPARTMENT_ADMIN && departmentId != null && departmentId.equals(caller.departmentId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized for department " + departmentId);
    }
}
