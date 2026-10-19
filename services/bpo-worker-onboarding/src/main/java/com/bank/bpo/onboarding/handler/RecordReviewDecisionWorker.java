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
 * cmp.record-review-decision: posts the reviewer's decision and its coded reason, or EXPIRED with SLA_EXPIRED set by the
 * SLA expiry event, to cmp.
 * Anything other than CLEAR, REJECT or EXPIRED is a contract fault (an incident), never a clearance.
 */
@Component
public class RecordReviewDecisionWorker {

    private static final Set<String> OUTPUTS = Set.of("reviewOutcome");
    private final ReviewCasePort cases;

    public RecordReviewDecisionWorker(ReviewCasePort cases) {
        this.cases = cases;
    }

    @JobWorker(type = "cmp.record-review-decision", fetchVariables = {"caseId", "reviewDecision", "decisionReason", "sourceRef"})
    public Map<String, Object> handle(@Variable String caseId, @Variable String reviewDecision,
                                      @Variable String decisionReason, @Variable String sourceRef) {
        // The decision comes from a reviewer's form or from the SLA expiry (EXPIRED); anything else, a missing one
        // included, is refused by the adapter as a contract fault.
        return JobErrors.guard(sourceRef, () -> OutputVariables.of(OUTPUTS,
            Map.of("reviewOutcome", cases.record(caseId, reviewDecision, decisionReason, IdempotencyKeys.of(caseId, "record-decision")).name())));
    }
}
