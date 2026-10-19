package com.bank.bpo.processapi.application;

import com.bank.bpo.processapi.domain.Actor;
import java.time.Instant;
import java.util.List;

/**
 * What a staff user may see of a task. {@code reference} is the owning service's record id (for a review, the
 * cmp case); it is read-only and only filled on a single-task read. {@code fourEyesBlocked} is also only worked
 * out on a single-task read: it is true when the user is the last recorded assignee of this checker task's maker
 * task, in any state, so the screen shows the block before the user claims or decides; claim and decision are
 * refused either way. {@code reasons} are the reason codes the user picks one of with the decision.
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
    List<String> reasons,
    String referenceName,
    String reference,
    boolean fourEyesBlocked) {

    public enum Assignment { UNASSIGNED, MINE, OTHER }
}
