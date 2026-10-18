package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.ReviewCasePort;
import com.bank.bpo.workerkit.Ids;
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
    public Outcome record(String caseId, String decision, String idempotencyKey) {
        Map<String, Object> answer = http.post("/api/v1/compliance/review-cases/" + Ids.require("caseId", caseId) + "/decision",
            Map.of("decision", Ids.oneOf("decision", decision, DECISIONS)), idempotencyKey);
        return Outcome.valueOf(Ids.oneOf("outcome", answer.get("outcome"), OUTCOMES));
    }
}
