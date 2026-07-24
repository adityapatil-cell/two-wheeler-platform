package com.twowheeler.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;

import java.util.List;

/**
 * Gateway reactive security config.
 *
 * NOTE: Spring Cloud Gateway runs on WebFlux (reactive) — NOT Spring MVC.
 * We cannot use @EnableWebSecurity or HttpSecurity here.
 * We use @EnableWebFluxSecurity and ServerHttpSecurity instead.
 *
 * Responsibilities:
 *   1. Validate Cognito JWT on every protected route
 *   2. Allow public routes through without JWT
 *   3. Forward validated JWT downstream to services (they trust it without re-validating)
 *   4. Reject expired/invalid JWTs with 401 before they reach any service
 *
 * Role mapping (custom:role Cognito claim → Spring authority):
 *   customer       → ROLE_CUSTOMER
 *   garage_staff   → ROLE_GARAGE_STAFF
 *   tow_driver     → ROLE_TOW_DRIVER
 *   platform_admin → ROLE_PLATFORM_ADMIN
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            // Disable CSRF — stateless JWT, no sessions
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // Authorization rules
            .authorizeExchange(exchanges -> exchanges

                // ─── Public routes — no JWT needed ──────────────────────────

                // Auth endpoints — user must register/login before they have a token
                .pathMatchers("/api/v1/auth/**").permitAll()

                // Public GET endpoints — browse listings and garages without login
                .pathMatchers(HttpMethod.GET, "/api/v1/listings/{id}").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/garages/{id}").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/reviews/target/**").permitAll()

                // Actuator health — needed for K8s liveness/readiness probes
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()

                // ─── Role-protected routes ────────────────────────────────────

                // Tow driver endpoints — only tow_driver role
                .pathMatchers("/api/v1/tow/*/accept",
                              "/api/v1/tow/*/pickup",
                              "/api/v1/tow/*/dropoff")
                    .hasRole("TOW_DRIVER")

                // Garage staff endpoints — only garage_staff role
                .pathMatchers(HttpMethod.POST, "/api/v1/repairs").hasRole("GARAGE_STAFF")
                .pathMatchers(HttpMethod.PATCH, "/api/v1/repairs/*/status").hasRole("GARAGE_STAFF")
                .pathMatchers("/api/v1/inventory/**").hasRole("GARAGE_STAFF")

                // Platform admin endpoints
                .pathMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")

                // ─── Everything else requires any valid JWT ───────────────────
                .anyExchange().authenticated()
            )

            // JWT resource server — validates against Cognito JWKS
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Reactive JWT converter — maps custom:role Cognito claim to Spring authorities.
     *
     * Wraps the standard JwtAuthenticationConverter in a reactive adapter
     * since Gateway is WebFlux-based.
     */
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("custom:role");
            if (role == null) return List.of();
            // "garage_staff" → ROLE_GARAGE_STAFF
            String authority = "ROLE_" + role.toUpperCase().replace("-", "_");
            return List.of(
                new org.springframework.security.core.authority
                    .SimpleGrantedAuthority(authority)
            );
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
