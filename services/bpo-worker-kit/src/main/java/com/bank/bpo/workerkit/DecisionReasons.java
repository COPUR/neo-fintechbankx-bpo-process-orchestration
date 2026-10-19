package com.bank.bpo.workerkit;

import java.util.Set;

/**
 * The coded reason every human decision carries (U4; models/dictionary/variables.yaml decisionReason). A reason is
 * a code, never free text. People pick from {@link #HUMAN}; SLA_EXPIRED is set only by the SLA expiry events, so it
 * goes with the expiry decision and with nothing else, and the expiry decision goes with nothing but SLA_EXPIRED.
 */
public final class DecisionReasons {

    /** Offered on the forms. OTHER only until the cmp and pay owners supply their codes. */
    public static final Set<String> HUMAN = Set.of("OTHER");
    public static final String SLA_EXPIRED = "SLA_EXPIRED";

    private DecisionReasons() {
    }

    /** {@code reason} for {@code decision}, or a CONTRACT failure (an incident, no call made) naming only the field. */
    public static String require(String decision, String expiredDecision, Object reason) {
        if (expiredDecision.equals(decision)) {
            return Ids.oneOf("reason", reason, Set.of(SLA_EXPIRED));
        }
        return Ids.oneOf("reason", reason, HUMAN);
    }
}
