package com.twowheeler.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Base Spring Security configuration — applied in all Spring MVC services.
 *
 * Key decisions:
 *   - Stateless (no sessions) — JWTs carry all auth state
 *   - JWT validated against Cognito JWKS endpoint (configured per service in application.yml)
 *   - Roles extracted from custom:role claim → ROLE_CUSTOMER, ROLE_GARAGE_STAFF etc.
 *   - @PreAuthorize enabled for method-level security in controllers
 *   - Public paths: /actuator/health, /v3/api-docs, /swagger-ui/**
 *
 * Each service imports this config via @Import(CommonSecurityConfig.class)
 * or component scan picks it up — they can override by defining their own
 * SecurityFilterChain bean with higher @Order.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class CommonSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless — no CSRF needed, no sessions
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no JWT required
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // JWT resource server — validates against Cognito JWKS
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Maps the custom:role Cognito claim to Spring Security ROLE_ authorities.
     *
     * custom:role = "customer"      → ROLE_CUSTOMER
     * custom:role = "garage_staff"  → ROLE_GARAGE_STAFF
     * custom:role = "tow_driver"    → ROLE_TOW_DRIVER
     * custom:role = "platform_admin"→ ROLE_PLATFORM_ADMIN
     *
     * Used in @PreAuthorize("hasRole('CUSTOMER')") annotations on controllers.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Read from custom:role claim instead of default "scope" claim
        authoritiesConverter.setAuthoritiesClaimName("custom:role");
        // Prefix with ROLE_ so hasRole() works without prefix
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("custom:role");
            if (role == null) return java.util.Collections.emptyList();
            // Convert "garage_staff" → ROLE_GARAGE_STAFF
            String authority = "ROLE_" + role.toUpperCase().replace("-", "_");
            return java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(authority)
            );
        });

        return converter;
    }
}
