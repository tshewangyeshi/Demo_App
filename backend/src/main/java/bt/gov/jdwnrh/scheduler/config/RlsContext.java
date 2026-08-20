package bt.gov.jdwnrh.scheduler.config;

import java.util.UUID;

import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * The authenticated caller's identity, as resolved from the verified JWT for
 * the current request. Populated by the (not-yet-built) JWT auth filter and
 * consumed by {@link RlsSessionInitializer} to set the Postgres RLS session
 * variables. Deliberately a plain immutable holder, not tied to Spring
 * Security's Authentication type, so it's trivial to construct directly in
 * tests without standing up the whole security filter chain.
 */
public record RlsContext(UUID userId, Role role, UUID departmentId) {

    public static RlsContext of(UUID userId, Role role, UUID departmentId) {
        return new RlsContext(userId, role, departmentId);
    }
}
