package bt.gov.jdwnrh.scheduler.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * Backs the per-request role/department re-check in JwtAuthenticationFilter.
 * Calls get_current_role_and_department (see V8), the same narrow
 * SECURITY DEFINER pattern as AuthLookupRepository, for the same reason:
 * this runs before any RlsContext exists for the request (it's what
 * DETERMINES the RlsContext).
 */
@Repository
public class CurrentRoleLookup {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Optional<CurrentRoleInfo> findByUserId(UUID userId) {
        var results = (List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM get_current_role_and_department(:userId)", Tuple.class)
                .setParameter("userId", userId)
                .getResultList();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        Tuple row = results.get(0);
        UUID departmentId = row.get("department_id") != null ? (UUID) row.get("department_id") : null;
        return Optional.of(new CurrentRoleInfo(
                Role.valueOf((String) row.get("role")),
                departmentId,
                (String) row.get("account_status")));
    }

    public record CurrentRoleInfo(Role role, UUID departmentId, String accountStatus) {
    }
}
