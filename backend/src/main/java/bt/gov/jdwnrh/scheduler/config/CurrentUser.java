package bt.gov.jdwnrh.scheduler.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Retrieves the RlsContext that JwtAuthenticationFilter attached to the current request's security context. */
@Component
public class CurrentUser {

    public RlsContext require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof RlsContext context)) {
            throw new IllegalStateException("No authenticated RlsContext for this request");
        }
        return context;
    }
}
