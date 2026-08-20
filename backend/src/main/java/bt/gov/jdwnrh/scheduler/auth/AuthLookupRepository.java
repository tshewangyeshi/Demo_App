package bt.gov.jdwnrh.scheduler.auth;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * Calls the find_user_credentials_by_email SECURITY DEFINER function (see
 * V6__auth_lookup_function.sql) — the one deliberate, narrow bypass of
 * app_user's RLS, needed because login happens before any RlsContext exists.
 * Nothing else in the app should read app_user this way.
 */
@Repository
public class AuthLookupRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Optional<UserCredentials> findByEmail(String email) {
        var results = (java.util.List<Tuple>) entityManager
                .createNativeQuery("SELECT * FROM find_user_credentials_by_email(:email)", Tuple.class)
                .setParameter("email", email)
                .getResultList();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        Tuple row = results.get(0);
        UUID departmentId = row.get("department_id") != null ? (UUID) row.get("department_id") : null;
        return Optional.of(new UserCredentials(
                (UUID) row.get("id"),
                (String) row.get("password_hash"),
                Role.valueOf((String) row.get("role")),
                departmentId,
                (String) row.get("account_status")));
    }
}
