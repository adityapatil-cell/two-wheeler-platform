package com.twowheeler.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global gateway filter — runs on every request.
 *
 * Responsibilities:
 *   1. Generate or extract a traceId for the request
 *   2. Add X-Trace-Id header to the downstream request so all services
 *      log with the same traceId (enabling cross-service log correlation in Kibana)
 *   3. Add X-Trace-Id to the response so the React app can log it for debugging
 *
 * TraceId priority:
 *   1. Use existing X-Trace-Id if already present (e.g. from an internal service call)
 *   2. Use OpenTelemetry span traceId if available
 *   3. Generate a new UUID as fallback
 *
 * Order(-1) ensures this runs before all other filters.
 */
@Slf4j
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get or generate traceId
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        final String finalTraceId = traceId;
        log.debug("Request [{}] {} traceId={}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath(),
            finalTraceId);

        // Mutate request — add traceId header for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest()
            .mutate()
            .header(TRACE_ID_HEADER, finalTraceId)
            .build();

        // Mutate response — add traceId header for React app
        exchange.getResponse()
            .getHeaders()
            .add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1; // Run before all other filters
    }
}
