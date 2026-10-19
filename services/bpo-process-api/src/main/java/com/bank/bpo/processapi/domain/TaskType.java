package com.bank.bpo.processapi.domain;

import java.util.List;
import java.util.Optional;

/**
 * One kind of user task the API serves: where it sits in a model, who works it and what may be written back.
 * The decision and its coded reason are the only things a staff user can send to the engine; ids, keys and
 * every other variable stay as the process set them (DevSecOps should-fix S-1 on e13de1f). Reasons are a fixed
 * list with no free text, so no personal data reaches the engine (U4).
 *
 * @param reasons the reason codes a person may pick; system-only codes such as SLA_EXPIRED are never in it
 * @param makerElementId the user task whose last assignee may not claim or complete this one (four eyes), or null
 * @param referenceVariable the process variable that points at the owning service's record (shown, never edited)
 */
public record TaskType(
    Actor actor,
    String processDefinitionId,
    String elementId,
    String decisionVariable,
    List<String> decisions,
    String reasonVariable,
    List<String> reasons,
    String makerElementId,
    String referenceVariable) {

    public TaskType {
        decisions = List.copyOf(decisions);
        reasons = List.copyOf(reasons);
    }

    public Optional<String> maker() {
        return Optional.ofNullable(makerElementId);
    }

    public boolean allows(String decision) {
        return decision != null && decisions.contains(decision);
    }

    public boolean allowsReason(String reason) {
        return reason != null && reasons.contains(reason);
    }
}
