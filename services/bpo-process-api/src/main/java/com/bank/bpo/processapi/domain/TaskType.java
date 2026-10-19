package com.bank.bpo.processapi.domain;

import java.util.List;
import java.util.Optional;

/**
 * One kind of user task the API serves: where it sits in a model, who works it and what may be written back.
 * The decision variable and its values are the only thing a staff user can send to the engine; ids, keys and
 * every other variable stay as the process set them (DevSecOps should-fix S-1 on e13de1f).
 *
 * @param makerElementId the user task whose completer may not complete this one (four eyes), or null
 * @param referenceVariable the process variable that points at the owning service's record (shown, never edited)
 */
public record TaskType(
    Actor actor,
    String processDefinitionId,
    String elementId,
    String decisionVariable,
    List<String> decisions,
    String makerElementId,
    String referenceVariable) {

    public TaskType {
        decisions = List.copyOf(decisions);
    }

    public Optional<String> maker() {
        return Optional.ofNullable(makerElementId);
    }

    public boolean allows(String decision) {
        return decision != null && decisions.contains(decision);
    }
}
