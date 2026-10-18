package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.ReviewCasePort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** cmp.open-review-case: opens the human review case for a REVIEW screening. */
@Component
public class OpenReviewCaseWorker {

    private static final Set<String> OUTPUTS = Set.of("caseId");
    private final ReviewCasePort cases;

    public OpenReviewCaseWorker(ReviewCasePort cases) {
        this.cases = cases;
    }

    @JobWorker(type = "cmp.open-review-case", fetchVariables = {"screeningId", "sourceRef", "sourceProcess", "jurisdiction"})
    public Map<String, Object> handle(@Variable String screeningId, @Variable String sourceRef,
                                      @Variable String sourceProcess, @Variable String jurisdiction) {
        return JobErrors.guard(sourceRef, () -> OutputVariables.of(OUTPUTS, Map.of("caseId",
            cases.open(screeningId, sourceRef, sourceProcess, jurisdiction, IdempotencyKeys.of(sourceRef, "open-review-case")))));
    }
}
