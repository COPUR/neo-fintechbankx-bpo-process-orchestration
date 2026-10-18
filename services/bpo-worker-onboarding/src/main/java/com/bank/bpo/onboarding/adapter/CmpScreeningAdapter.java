package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.ScreeningPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** cmp POST /api/v1/compliance/onboarding-screenings (contracts/expectations/cmp.yaml). */
public final class CmpScreeningAdapter implements ScreeningPort {

    private static final Set<String> DECISIONS = Set.of("PASS", "REVIEW", "FAIL");
    private final ProviderHttp http;

    public CmpScreeningAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public Result screen(String applicationId, String idempotencyKey) {
        Map<String, Object> answer = http.post("/api/v1/compliance/onboarding-screenings",
            Map.of("applicationId", Ids.require("applicationId", applicationId)), idempotencyKey);
        // A decision this adapter does not know is a contract fault (an incident), never a clearance.
        Decision decision = switch (Ids.oneOf("decision", answer.get("decision"), DECISIONS)) {
            case "PASS" -> Decision.CLEAR;
            case "REVIEW" -> Decision.REVIEW;
            default -> Decision.REJECT;
        };
        return new Result(Ids.require("screeningId", answer.get("screeningId")), decision);
    }
}
