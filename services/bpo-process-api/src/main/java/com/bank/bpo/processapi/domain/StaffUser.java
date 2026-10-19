package com.bank.bpo.processapi.domain;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The signed-in staff member, from the DPoP-bound access token: the id the engine records as assignee, the
 * Keycloak groups that name review queues, and the names the engine may have recorded for the same person before
 * the assignee became the token subject. Former ids are used only to refuse four eyes, never to grant anything.
 * No personal data beyond the subject id and the user name.
 */
public record StaffUser(String id, Set<String> groups, Set<String> formerIds) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    public StaffUser {
        if (id == null || !NAME.matcher(id).matches()) {
            throw new IllegalArgumentException("the staff user id is not a plain id");
        }
        groups = Set.copyOf(groups);
        formerIds = Set.copyOf(formerIds);
        if (!formerIds.stream().allMatch(f -> NAME.matcher(f).matches())) {
            throw new IllegalArgumentException("a former staff user name is not a plain user name");
        }
    }

    public StaffUser(String id, Set<String> groups) {
        this(id, groups, Set.of());
    }

    /** True when the engine may have recorded this person under {@code assignee}. */
    public boolean mayBe(String assignee) {
        return id.equals(assignee) || formerIds.contains(assignee);
    }
}
