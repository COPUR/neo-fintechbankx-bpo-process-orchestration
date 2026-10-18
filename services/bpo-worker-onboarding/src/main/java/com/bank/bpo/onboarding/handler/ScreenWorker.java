package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.ScreeningPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** cmp.screen: asks cmp to screen the applicant; the decision of record stays in cmp. */
@Component
public class ScreenWorker {

    private static final Set<String> OUTPUTS = Set.of("screeningId", "screeningDecision");
    private final ScreeningPort screening;

    public ScreenWorker(ScreeningPort screening) {
        this.screening = screening;
    }

    @JobWorker(type = "cmp.screen", fetchVariables = {"applicationId"})
    public Map<String, Object> handle(@Variable String applicationId) {
        return JobErrors.guard(applicationId, () -> {
            ScreeningPort.Result result = screening.screen(applicationId, IdempotencyKeys.of(applicationId, "screen"));
            return OutputVariables.of(OUTPUTS, Map.of("screeningId", result.screeningId(), "screeningDecision", result.decision().name()));
        });
    }
}
