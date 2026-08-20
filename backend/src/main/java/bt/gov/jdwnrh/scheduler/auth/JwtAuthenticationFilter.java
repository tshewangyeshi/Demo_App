package bt.gov.jdwnrh.scheduler.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

                currentRoleLookup.findByUserId(verified.userId()).ifPresent(current -> {
                    if (!"ACTIVE".equals(current.accountStatus())) {
                        return; // account suspended/deactivated since the token was issued — leave unauthenticated
                    }
                    RlsContext rlsContext = new RlsContext(verified.userId(), current.role(), current.departmentId());
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + current.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(rlsContext, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (RuntimeException ex) {
                // Malformed/expired/invalid-signature token: leave unauthenticated, don't leak details.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
