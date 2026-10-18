package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.ScreeningPort;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

/** Maps cmp's catalog decision (PASS, REVIEW, FAIL) to the process's (CLEAR, REVIEW, REJECT). */
public final class CmpScreeningAdapter implements ScreeningPort {

    private final ProviderHttp http;

    public CmpScreeningAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public Result screen(String applicationId, String idempotencyKey) {
        Map<String, Object> answer = http.post("/api/v1/compliance/onboarding-screenings",
            Map.of("applicationId", applicationId), idempotencyKey);
        Decision decision = switch (String.valueOf(answer.get("decision"))) {
            case "PASS" -> Decision.CLEAR;
            case "REVIEW" -> Decision.REVIEW;
            default -> Decision.REJECT; // FAIL, and anything this adapter does not know, is not a clearance
        };
        return new Result(String.valueOf(answer.get("screeningId")), decision);
    }
}
