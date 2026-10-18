package com.bank.bpo.processapi.domain;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The signed-in staff member, from the DPoP-bound access token: the user name the engine records as assignee
 * and the Keycloak groups that name review queues. No personal data beyond the staff user name.
 */
public record StaffUser(String id, Set<String> groups) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    public StaffUser {
        if (id == null || !NAME.matcher(id).matches()) {
            throw new IllegalArgumentException("the staff user name is not a plain user name");
        }
        groups = Set.copyOf(groups);
    }
}
