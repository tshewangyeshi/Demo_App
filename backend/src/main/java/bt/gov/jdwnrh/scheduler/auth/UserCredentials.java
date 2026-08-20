package bt.gov.jdwnrh.scheduler.auth;

import java.util.UUID;

import bt.gov.jdwnrh.scheduler.iam.Role;

/** Projection returned by the find_user_credentials_by_email SECURITY DEFINER function — see AuthLookupRepository. */
public record UserCredentials(
        UUID id,
        String passwordHash,
        Role role,
        UUID departmentId,
        String accountStatus) {
}
