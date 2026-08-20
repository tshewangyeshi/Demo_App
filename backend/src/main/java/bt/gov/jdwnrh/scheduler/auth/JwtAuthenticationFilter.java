package bt.gov.jdwnrh.scheduler.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import bt.gov.jdwnrh.scheduler.config.RlsContext;

/**
 * Verifies the access token, then re-checks the user's CURRENT role/
 * department against the DB rather than trusting the token's embedded
 * claims (see CurrentRoleLookup) — this is what makes a role change take
 * effect on the very next request instead of waiting for the 15-minute
 * access token to expire. On any failure this simply leaves the security
 * context unauthenticated; Spring Security's authorization rules (see
 * SecurityConfig) turn that into a 401/403 for protected endpoints.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CurrentRoleLookup currentRoleLookup;

    public JwtAuthenticationFilter(JwtService jwtService, CurrentRoleLookup currentRoleLookup) {
        this.jwtService = jwtService;
        this.currentRoleLookup = currentRoleLookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var verified = jwtService.verify(header.substring("Bearer ".length()));

                currentRoleLookup.findByUserId(verified.userId()).ifPresentOrElse(current -> {
                    if (!"ACTIVE".equals(current.accountStatus())) {
                        // Security-relevant: a valid, unexpired token being presented for an account
                        // suspended/deactivated AFTER the token was issued — worth being able to find in logs.
                        log.info("Rejected request: account {} status={} (token still valid)", verified.userId(), current.accountStatus());
                        return;
                    }
                    RlsContext rlsContext = new RlsContext(verified.userId(), current.role(), current.departmentId());
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + current.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(rlsContext, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }, () -> log.warn("Rejected request: token valid but user {} no longer exists", verified.userId()));
            } catch (RuntimeException ex) {
                // Malformed/expired/invalid-signature token — routine (access tokens expire every
                // 15 minutes by design), not necessarily suspicious, so DEBUG rather than WARN. Never
                // logs the token itself or the exception's full message, just what kind of failure it was.
                log.debug("Rejected request: invalid access token ({})", ex.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
