package com.twowheeler.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Extracts typed claims from the Cognito JWT.
 *
 * Cognito JWT custom claims (set during Phase 1 OIDC design):
 *   sub                → unique Cognito user ID
 *   phone_number       → user's phone number
 *   custom:role        → customer | garage_staff | tow_driver | platform_admin
 *   custom:user_id     → our platform user UUID
 *   custom:garage_id   → garage UUID (garage_staff only, null otherwise)
 *   custom:driver_id   → driver UUID (tow_driver only, null otherwise)
 *   cognito:groups     → list of Cognito groups user belongs to
 *
 * Usage in service layer:
 *   String userId = jwtClaimsExtractor.getUserId();
 *   String garageId = jwtClaimsExtractor.getGarageId()
 *       .orElseThrow(() -> ApiException.forbidden("No garage assigned"));
 */
@Component
public class JwtClaimsExtractor {

    public Jwt getJwt() {
        return (Jwt) SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal();
    }

    /** Cognito sub — the stable unique identifier for this user in Cognito */
    public String getCognitoSub() {
        return getJwt().getSubject();
    }

    /** Our platform user UUID (custom:user_id claim) */
    public String getUserId() {
        return getJwt().getClaimAsString("custom:user_id");
    }

    /** Role from custom:role claim */
    public String getRole() {
        return getJwt().getClaimAsString("custom:role");
    }

    /** Phone number from Cognito standard claim */
    public String getPhoneNumber() {
        return getJwt().getClaimAsString("phone_number");
    }

    /** Garage ID — only present for garage_staff tokens */
    public Optional<String> getGarageId() {
        return Optional.ofNullable(getJwt().getClaimAsString("custom:garage_id"));
    }

    /** Driver ID — only present for tow_driver tokens */
    public Optional<String> getDriverId() {
        return Optional.ofNullable(getJwt().getClaimAsString("custom:driver_id"));
    }

    /** Cognito group membership list */
    @SuppressWarnings("unchecked")
    public List<String> getGroups() {
        List<String> groups = getJwt().getClaimAsStringList("cognito:groups");
        return groups != null ? groups : List.of();
    }

    /** Convenience check — is this token from a customer? */
    public boolean isCustomer() {
        return "customer".equals(getRole());
    }

    /** Convenience check — is this token from a garage staff member? */
    public boolean isGarageStaff() {
        return "garage_staff".equals(getRole());
    }

    /** Convenience check — is this token from a tow driver? */
    public boolean isTowDriver() {
        return "tow_driver".equals(getRole());
    }
}
