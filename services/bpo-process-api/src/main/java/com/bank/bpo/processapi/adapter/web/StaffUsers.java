package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.domain.StaffUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the staff user from the verified access token: the user name claim and the groups claim (Keycloak's
 * group mapper writes full paths such as "/cmp-tr-onboarding-l1"; the leading slash is dropped).
 */
public final class StaffUsers {

    private final String userClaim;
    private final String groupsClaim;

    public StaffUsers(String userClaim, String groupsClaim) {
        this.userClaim = userClaim;
        this.groupsClaim = groupsClaim;
    }

    public StaffUser from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("no verified access token");
        }
        Set<String> groups = new LinkedHashSet<>();
        if (jwt.getClaim(groupsClaim) instanceof List<?> list) {
            for (Object g : list) {
                if (g instanceof String s && !s.isBlank()) {
                    groups.add(s.startsWith("/") ? s.substring(1) : s);
                }
            }
        }
        return new StaffUser(jwt.getClaimAsString(userClaim), groups);
    }
}
