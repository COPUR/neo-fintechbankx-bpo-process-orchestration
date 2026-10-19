package com.bank.bpo.processapi.application;

import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.StaffUser;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The queue groups of each actor: the candidate groups the routing DMNs write into the user tasks. A user acts
 * as an actor when their token carries at least one of that actor's groups, and only sees tasks of those groups.
 */
public final class QueueGroups {

    private final Map<Actor, Set<String>> byActor;

    public QueueGroups(Map<Actor, Set<String>> byActor) {
        EnumMap<Actor, Set<String>> copy = new EnumMap<>(Actor.class);
        byActor.forEach((actor, groups) -> copy.put(actor, Set.copyOf(groups)));
        this.byActor = copy;
    }

    /** The user's groups that are queues of {@code actor}; empty when the user does not act as it. */
    public Set<String> of(Actor actor, StaffUser user) {
        Set<String> mine = new LinkedHashSet<>(byActor.getOrDefault(actor, Set.of()));
        mine.retainAll(user.groups());
        return mine;
    }

    /** The actors the user acts as, in declaration order. */
    public List<Actor> actorsOf(StaffUser user) {
        return java.util.Arrays.stream(Actor.values()).filter(a -> !of(a, user).isEmpty()).toList();
    }
}
