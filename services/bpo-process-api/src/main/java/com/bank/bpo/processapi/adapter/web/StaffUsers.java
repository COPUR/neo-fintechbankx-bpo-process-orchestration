package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.domain.StaffUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the staff user from the verified access token. The user id is the claim the engine records as assignee
 * ({@code sub}: it never changes, so four eyes survives a rename in the directory); the display name is shown
 * on the screen only. Queue groups arrive as directory group paths under a fixed prefix (for example
 * "/staff/fbx-pay-tr-fast-ops" for the queue "pay-tr-fast-ops"); the prefix is dropped, and every group outside
 * it, such as the staff role groups, names no queue and is ignored.
 */
public final class StaffUsers {

    private static final Pattern DISPLAY = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    private final String userClaim;
    private final String displayClaim;
    private final String groupsClaim;
    private final String groupPrefix;

    public StaffUsers(String userClaim, String displayClaim, String groupsClaim, String groupPrefix) {
        if (groupPrefix == null || groupPrefix.isBlank()) {
            throw new IllegalStateException("the queue group prefix is required");
        }
        this.userClaim = userClaim;
        this.displayClaim = displayClaim;
        this.groupsClaim = groupsClaim;
        this.groupPrefix = groupPrefix;
    }

    public StaffUser from(Authentication authentication) {
        Jwt jwt = jwt(authentication);
        Set<String> groups = new LinkedHashSet<>();
        if (jwt.getClaim(groupsClaim) instanceof List<?> list) {
            for (Object g : list) {
                if (g instanceof String s && s.startsWith(groupPrefix) && s.length() > groupPrefix.length()) {
                    groups.add(s.substring(groupPrefix.length()));
                }
            }
        }
        return new StaffUser(jwt.getClaimAsString(userClaim), groups);
    }

    /** The name to greet the user with, when the token carries a plain one; it is never used to decide anything. */
    public Optional<String> displayName(Authentication authentication) {
        return Optional.ofNullable(jwt(authentication).getClaimAsString(displayClaim))
            .filter(name -> DISPLAY.matcher(name).matches());
    }

    private static Jwt jwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("no verified access token");
        }
        return jwt;
    }
}
