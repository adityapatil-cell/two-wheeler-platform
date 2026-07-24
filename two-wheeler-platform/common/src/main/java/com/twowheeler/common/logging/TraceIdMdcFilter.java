package com.twowheeler.common.logging;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter — runs once per request.
 *
 * Injects the OpenTelemetry traceId into the SLF4J MDC so every log line
 * produced during this request automatically includes it.
 *
 * Log output example (Logback pattern):
 *   2024-06-01 10:00:00 INFO  [workshop-service] [traceId=abc-123-xyz] RepairService - Repair order created
 *
 * This is how you search one user journey across all services in Kibana:
 *   traceId: "abc-123-xyz"  → shows logs from gateway, workshop, notification in one view
 *
 * Also adds X-Trace-Id to every HTTP response header so the React app
 * and Postman can log the traceId for debugging.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TraceIdMdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACE_ID_HEADER  = "X-Trace-Id";

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = null;
        try {
            // Get traceId from the current OpenTelemetry span
            if (tracer.currentSpan() != null) {
                traceId = tracer.currentSpan().context().traceId();
            }

            if (traceId != null) {
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                // Add to response header so clients can log it
                response.setHeader(TRACE_ID_HEADER, traceId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clean up MDC to avoid leaking into thread pool threads
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
