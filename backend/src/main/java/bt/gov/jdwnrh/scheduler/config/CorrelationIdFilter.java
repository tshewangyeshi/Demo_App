package bt.gov.jdwnrh.scheduler.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * See design doc, "Observability": every log line should carry a request
 * correlation ID (application.yml's logging.pattern.console already has the
 * %X{correlationId} placeholder — this is what actually populates it).
 * Reuses an incoming X-Correlation-Id header if the caller/load balancer
 * already set one, otherwise generates a fresh one; echoes it back on the
 * response so a client (or a support ticket) can reference the exact
 * request. Runs at HIGHEST_PRECEDENCE — before Spring Security's filter
 * chain — so even a 401/403/permitAll request gets a correlation ID in its
 * logs, not just authenticated ones. MDC is cleared in a finally block:
 * threads are reused across requests (servlet container thread pool), so
 * leaving a stale value would bleed into the next unrelated request's logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
