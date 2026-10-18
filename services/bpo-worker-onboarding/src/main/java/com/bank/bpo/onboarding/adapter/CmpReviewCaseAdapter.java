package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.ReviewCasePort;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

public final class CmpReviewCaseAdapter implements ReviewCasePort {

    private final ProviderHttp http;

    public CmpReviewCaseAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String open(String screeningId, String sourceRef, String idempotencyKey) {
        return String.valueOf(http.post("/api/v1/compliance/review-cases",
            Map.of("screeningId", screeningId, "sourceRef", sourceRef), idempotencyKey).get("caseId"));
    }

    @Override
    public Outcome record(String caseId, String decision, String idempotencyKey) {
        Map<String, Object> answer = http.post("/api/v1/compliance/review-cases/" + caseId + "/decision",
            Map.of("decision", decision), idempotencyKey);
        return "CLEARED".equals(answer.get("outcome")) ? Outcome.CLEARED : Outcome.REJECTED;
    }
}
