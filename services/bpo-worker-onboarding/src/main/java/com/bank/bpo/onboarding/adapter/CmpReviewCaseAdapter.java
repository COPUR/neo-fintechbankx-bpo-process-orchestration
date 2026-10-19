package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.ReviewCasePort;
import com.bank.bpo.workerkit.DecisionReasons;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderException;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** cmp review cases: open, then record the reviewer's decision (contracts/expectations/cmp.yaml). */
public final class CmpReviewCaseAdapter implements ReviewCasePort {

    private static final Set<String> JURISDICTIONS = Set.of("AE", "TR");
    private static final Set<String> DECISIONS = Set.of("CLEAR", "REJECT", "EXPIRED");
    private static final Set<String> OUTCOMES = Set.of("CLEARED", "REJECTED");
    private final ProviderHttp http;

    public CmpReviewCaseAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String open(String screeningId, String sourceRef, String sourceProcess, String jurisdiction, String idempotencyKey) {
        Map<String, Object> request = Map.of(
            "screeningId", Ids.require("screeningId", screeningId),
            "sourceRef", Ids.require("sourceRef", sourceRef),
            "sourceProcess", Ids.require("sourceProcess", sourceProcess),
            "jurisdiction", Ids.oneOf("jurisdiction", jurisdiction, JURISDICTIONS));
        return Ids.require("caseId", http.post("/api/v1/compliance/review-cases", request, idempotencyKey).get("caseId"));
    }

    @Override
    public Outcome record(String caseId, String decision, String reason, String idempotencyKey) {
        String path = "/api/v1/compliance/review-cases/" + Ids.require("caseId", caseId) + "/decision";
        Map<String, Object> answer = http.post(path, Map.of(
            "decision", Ids.oneOf("decision", decision, DECISIONS),
            "reason", DecisionReasons.require(decision, "EXPIRED", reason)), idempotencyKey);
        String outcome = Ids.oneOf("outcome", answer.get("outcome"), OUTCOMES);
        if ("EXPIRED".equals(decision) && "CLEARED".equals(outcome)) {
            // Fail closed (C8-K9): a review nobody decided is never a clearance, whatever cmp answers.
            throw new ProviderException(ProviderException.Kind.CONTRACT, "OUTCOME_NOT_ALLOWED",
                "outcome is CLEARED for an EXPIRED decision", null);
        }
        return Outcome.valueOf(outcome);
    }
}
