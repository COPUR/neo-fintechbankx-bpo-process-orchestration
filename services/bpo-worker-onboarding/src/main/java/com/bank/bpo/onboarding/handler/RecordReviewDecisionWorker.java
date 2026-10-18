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

/**
 * cmp.record-review-decision: posts the reviewer's decision (or EXPIRED when nobody decided) to cmp.
 * An absent decision is posted as EXPIRED, never as a clearance.
 */
@Component
public class RecordReviewDecisionWorker {

    private static final Set<String> OUTPUTS = Set.of("reviewOutcome");
    private final ReviewCasePort cases;

    public RecordReviewDecisionWorker(ReviewCasePort cases) {
        this.cases = cases;
    }

    @JobWorker(type = "cmp.record-review-decision", fetchVariables = {"caseId", "reviewDecision"})
    public Map<String, Object> handle(@Variable String caseId, @Variable String reviewDecision) {
        String decision = reviewDecision == null || reviewDecision.isBlank() ? "EXPIRED" : reviewDecision;
        return JobErrors.guard(() -> OutputVariables.of(OUTPUTS,
            Map.of("reviewOutcome", cases.record(caseId, decision, IdempotencyKeys.of(caseId, "record-decision")).name())));
    }
}
