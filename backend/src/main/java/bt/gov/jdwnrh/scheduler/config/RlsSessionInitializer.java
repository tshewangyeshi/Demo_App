package bt.gov.jdwnrh.scheduler.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets the Postgres session variables the RLS policies in
 * V3__row_level_security.sql key off: app.current_role, app.current_user_id,
 * app.current_department_id.
 *
 * MUST be the first thing called inside every application-service method
 * that touches an RLS-protected table (Appointment, DoctorSchedule, AppUser)
 * — deliberately an explicit call at the top of each method rather than an
 * AOP aspect, so it's obvious from reading the method that RLS context is in
 * effect, and there's no aspect-ordering footgun to get wrong.
 *
 * Propagation.MANDATORY: this must run inside an existing transaction using
 * the app_user (non-owner) connection — SET LOCAL only affects the current
 * transaction, on the current physical connection, which only holds for a
 * conventional Spring Boot server (HikariCP, one connection per transaction)
 * connecting DIRECTLY to Postgres, never through a pooled/pgbouncer
 * transaction-mode endpoint. See docs/designs/jdwnrh-scheduler.md,
 * "RLS session variables and connection pooling".
 */
@Component
public class RlsSessionInitializer {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCurrentContext(RlsContext context) {
        setLocal("app.current_role", context.role().name());
        setLocal("app.current_user_id", context.userId().toString());
        setLocal("app.current_department_id",
                context.departmentId() != null ? context.departmentId().toString() : "");
    }

    private void setLocal(String settingName, String value) {
        // set_config(..., true) is the parameterized equivalent of
        // "SET LOCAL x = 'value'" — safe against injection, unlike string-
        // concatenating SET LOCAL directly (SET does not accept bind params).
        entityManager.createNativeQuery("SELECT set_config(:name, :value, true)")
                .setParameter("name", settingName)
                .setParameter("value", value)
                .getSingleResult();
    }
}
