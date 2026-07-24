package com.twowheeler.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter — propagates JWT claims as HTTP headers to downstream services.
 *
 * Why this is needed:
 *   Downstream services (workshop-service, platform-service etc.) trust the gateway
 *   and do NOT re-validate the JWT. Instead they read user identity from these headers.
 *   This avoids each service needing to parse the JWT itself.
 *
 * Headers added (matching the Cognito claim design from Phase 1 OIDC):
 *   X-User-Id      → custom:user_id (our platform UUID)
 *   X-User-Role    → custom:role (customer | garage_staff | tow_driver | platform_admin)
 *   X-Cognito-Sub  → sub (Cognito's stable user identifier)
 *   X-Garage-Id    → custom:garage_id (only for garage_staff, empty otherwise)
 *   X-Driver-Id    → custom:driver_id (only for tow_driver, empty otherwise)
 *
 * Services read these in their controllers:
 *   @RequestHeader("X-User-Id") String userId
 *   @RequestHeader("X-User-Role") String role
 *
 * Order(0) — runs after TraceIdGatewayFilter (Order -1) but before routing.
 */
@Slf4j
@Component
public class AuthHeaderPropagationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt jwt = (Jwt) jwtAuth.getPrincipal();

                    // Extract claims and add as headers
                    var mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id",
                            getClaimOrEmpty(jwt, "custom:user_id"))
                        .header("X-User-Role",
                            getClaimOrEmpty(jwt, "custom:role"))
                        .header("X-Cognito-Sub",
                            jwt.getSubject() != null ? jwt.getSubject() : "")
                        .header("X-Garage-Id",
                            getClaimOrEmpty(jwt, "custom:garage_id"))
                        .header("X-Driver-Id",
                            getClaimOrEmpty(jwt, "custom:driver_id"))
                        .build();

                    log.debug("Propagating auth headers: userId={} role={}",
                        getClaimOrEmpty(jwt, "custom:user_id"),
                        getClaimOrEmpty(jwt, "custom:role"));

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }
                // No auth context (public route) — pass through as-is
                return chain.filter(exchange);
            })
            // If no security context (unauthenticated public route) — pass through
            .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private String getClaimOrEmpty(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        return value != null ? value : "";
    }
}
