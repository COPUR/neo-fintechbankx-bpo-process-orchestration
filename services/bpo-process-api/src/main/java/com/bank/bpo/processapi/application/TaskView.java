package com.bank.bpo.processapi.application;

import com.bank.bpo.processapi.domain.Actor;
import java.time.Instant;
import java.util.List;

/**
 * What a staff user may see of a task. {@code reference} is the owning service's record id (for a review, the
 * cmp case); it is read-only and only filled on a single-task read. {@code fourEyesBlocked} is also only worked
 * out on a single-task read: it is true when the user held the maker task of this checker task, in any state, so
 * the screen shows the block before the user decides; the decision is refused either way.
 */
public record TaskView(
    long taskKey,
    Actor actor,
    String taskType,
    String processDefinitionId,
    Assignment assignment,
    Instant createdAt,
    Instant dueAt,
    boolean fourEyes,
    List<String> decisions,
    String referenceName,
    String reference,
    boolean fourEyesBlocked) {

    public enum Assignment { UNASSIGNED, MINE, OTHER }
}
