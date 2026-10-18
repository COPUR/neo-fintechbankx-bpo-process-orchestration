package com.bank.bpo.processapi.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A human role that completes user tasks in the bpo models. Each actor has its own controller, its own
 * queue groups (configured from the routing DMNs) and its own screens in the staff web.
 */
public enum Actor {

    /** First-line compliance reviewer: the analyst queue of lib-screening-review (cmp-review-routing reviewQueue). */
    COMPLIANCE_ANALYST("compliance-analyst"),
    /** Senior reviewer (MLRO in AE, compliance officer in TR): the escalation queue (cmp-review-routing escalationQueue). */
    COMPLIANCE_SENIOR("compliance-senior"),
    /** Payments operations: records what the scheme shows for an instant payment exception (pay-exception-routing opsQueue). */
    PAYMENTS_OPERATIONS("payments-operations"),
    /** Payments supervisor: decides escalated or expired exceptions, never one they escalated (supervisorQueue). */
    PAYMENTS_SUPERVISOR("payments-supervisor");

    private final String id;

    Actor(String id) {
        this.id = id;
    }

    /** The id used in URLs and configuration, for example {@code compliance-analyst}. */
    public String id() {
        return id;
    }

    public static Optional<Actor> fromId(String id) {
        return Arrays.stream(values()).filter(a -> a.id.equals(id)).findFirst();
    }
}
