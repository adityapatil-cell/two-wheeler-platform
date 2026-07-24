package com.twowheeler.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Rate limiter configuration — Redis-backed, per-user rate limiting.
 *
 * Rate limit strategy (from Phase 5 WAF decision):
 *   Authenticated users → rate limited by their Cognito sub (userId)
 *   Public/unauthenticated → rate limited by IP address
 *
 * Limits (configured in application.yml per route):
 *   20 requests/second sustained, burst up to 40
 *
 * This prevents:
 *   - Brute force attacks on auth endpoints
 *   - A single user overwhelming the towing dispatch service
 *   - Bot scraping of the marketplace listings
 *
 * The KeyResolver bean name "userKeyResolver" is referenced in
 * application.yml: key-resolver: "#{@userKeyResolver}"
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .flatMap(principal -> {
                if (principal instanceof JwtAuthenticationToken jwtAuth) {
                    // Authenticated — rate limit per Cognito sub (unique per user)
                    String sub = jwtAuth.getToken().getSubject();
                    return Mono.just("user:" + sub);
                }
                // Unauthenticated — rate limit per IP address
                String ip = exchange.getRequest()
                    .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
                return Mono.just("ip:" + ip);
            })
            // Fallback if no principal at all
            .switchIfEmpty(Mono.just("anonymous"));
    }
}
