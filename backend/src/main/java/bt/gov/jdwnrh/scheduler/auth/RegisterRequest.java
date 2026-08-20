package bt.gov.jdwnrh.scheduler.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-registration is PATIENT-only — see V7__self_registration_policy.sql and the RBAC design. */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank String firstName,
        @NotBlank String lastName) {
}
