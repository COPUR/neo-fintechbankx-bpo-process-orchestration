package com.bank.bpo.processapi.domain;

import java.time.Instant;
import java.util.List;

/** A user task as the engine reports it. Ids and states only; the engine holds no money and no personal data. */
public record EngineTask(
    long key,
    String processDefinitionId,
    String elementId,
    String state,
    String assignee,
    List<String> candidateGroups,
    long processInstanceKey,
    Instant creationDate,
    Instant dueDate) {

    public static final String OPEN = "CREATED";

    public EngineTask {
        candidateGroups = candidateGroups == null ? List.of() : List.copyOf(candidateGroups);
    }

    public boolean open() {
        return OPEN.equals(state);
    }
}
