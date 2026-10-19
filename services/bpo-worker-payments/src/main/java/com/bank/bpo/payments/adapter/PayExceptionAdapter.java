package com.bank.bpo.payments.adapter;

import com.bank.bpo.payments.port.PaymentExceptionPort;
import com.bank.bpo.workerkit.DecisionReasons;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderException;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** pay payment exceptions: record the decision, mark settled or rejected (contracts/expectations/pay.yaml). */
public final class PayExceptionAdapter implements PaymentExceptionPort {

    private static final Set<String> DECISIONS = Set.of("SETTLED", "REJECTED", "UNRESOLVED", "EXPIRED");
    private static final Set<String> OUTCOMES = Set.of("SETTLE", "RELEASE", "HANDED_BACK");
    /** Decisions that never move money: pay must answer HANDED_BACK, so the hold stays. */
    private static final Set<String> HAND_BACK_ONLY = Set.of("UNRESOLVED", "EXPIRED");
    private final ProviderHttp http;

    public PayExceptionAdapter(ProviderHttp http) {
        this.http = http;
    }

    private static String base(String paymentId) {
        return "/api/v1/payments/" + Ids.require("paymentId", paymentId) + "/exception";
    }

    @Override
    public Outcome record(String paymentId, String decision, String reason, String idempotencyKey) {
        String path = base(paymentId) + "/decision";
        Map<String, Object> answer = http.post(path, Map.of(
            "decision", Ids.oneOf("decision", decision, DECISIONS),
            "reason", DecisionReasons.require(decision, "EXPIRED", reason)), idempotencyKey);
        String outcome = Ids.oneOf("outcome", answer.get("outcome"), OUTCOMES);
        if (HAND_BACK_ONLY.contains(decision) && !"HANDED_BACK".equals(outcome)) {
            // A decision nobody could confirm must never capture or release the hold, whatever pay answers.
            throw new ProviderException(ProviderException.Kind.CONTRACT, "OUTCOME_NOT_ALLOWED",
                "outcome is not HANDED_BACK for a " + decision + " decision", null);
        }
        return Outcome.valueOf(outcome);
    }

    @Override
    public void markSettled(String paymentId, String journalRef, String idempotencyKey) {
        String path = base(paymentId) + "/settle";
        http.post(path, Map.of("journalRef", Ids.require("journalRef", journalRef)), idempotencyKey);
    }

    @Override
    public void markRejected(String paymentId, String idempotencyKey) {
        http.post(base(paymentId) + "/reject", Map.of(), idempotencyKey);
    }
}
