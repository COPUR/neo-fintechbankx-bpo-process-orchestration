package com.bank.bpo.processapi.domain;

import java.util.List;
import java.util.Optional;

/**
 * The user tasks the API serves, one entry per (process, element). A task of any other kind is invisible
 * through the API (fail closed): it is neither listed nor claimable nor completable. A test keeps this list
 * equal to the user tasks and forms in models/.
 */
public final class TaskTypes {

    public static final TaskType SCREENING_ANALYST_REVIEW = new TaskType(Actor.COMPLIANCE_ANALYST,
        "lib-screening-review", "Task_AnalystReview", "reviewDecision", List.of("CLEAR", "REJECT", "ESCALATE"),
        null, "caseId");

    public static final TaskType SCREENING_SENIOR_REVIEW = new TaskType(Actor.COMPLIANCE_SENIOR,
        "lib-screening-review", "Task_SeniorReview", "reviewDecision", List.of("CLEAR", "REJECT"),
        "Task_AnalystReview", "caseId");

    public static final TaskType PAYMENT_EXCEPTION_OPS_REVIEW = new TaskType(Actor.PAYMENTS_OPERATIONS,
        "pay-instant-exception", "Task_OpsReview", "exceptionDecision", List.of("SETTLED", "REJECTED", "ESCALATE"),
        null, "paymentId");

    public static final TaskType PAYMENT_EXCEPTION_SUPERVISOR_REVIEW = new TaskType(Actor.PAYMENTS_SUPERVISOR,
        "pay-instant-exception", "Task_SupervisorReview", "exceptionDecision", List.of("SETTLED", "REJECTED", "UNRESOLVED"),
        "Task_OpsReview", "paymentId");

    private static final List<TaskType> ALL = List.of(SCREENING_ANALYST_REVIEW, SCREENING_SENIOR_REVIEW,
        PAYMENT_EXCEPTION_OPS_REVIEW, PAYMENT_EXCEPTION_SUPERVISOR_REVIEW);

    private TaskTypes() {
    }

    public static List<TaskType> all() {
        return ALL;
    }

    public static List<TaskType> of(Actor actor) {
        return ALL.stream().filter(t -> t.actor() == actor).toList();
    }

    public static Optional<TaskType> find(String processDefinitionId, String elementId) {
        return ALL.stream()
            .filter(t -> t.processDefinitionId().equals(processDefinitionId) && t.elementId().equals(elementId))
            .findFirst();
    }
}
