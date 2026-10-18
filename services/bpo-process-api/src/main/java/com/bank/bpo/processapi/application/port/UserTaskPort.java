package com.bank.bpo.processapi.application.port;

import com.bank.bpo.processapi.domain.EngineTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The engine's user-task operations the API needs. Implementations throw
 * {@link com.bank.bpo.processapi.domain.TaskRefusal} with ENGINE_UNAVAILABLE when the engine cannot be reached
 * (nothing was sent), OUTCOME_UNKNOWN when a write was sent but its answer was lost, and TASK_CLAIMED_BY_OTHER
 * when an assignment conflicts.
 */
public interface UserTaskPort {

    /** Open tasks of one element whose candidate groups include any of {@code groups}. */
    List<EngineTask> openTasks(String processDefinitionId, String elementId, Set<String> groups, int limit);

    Optional<EngineTask> find(long taskKey);

    /** A string variable visible to the task, or empty when the task has none by that name. */
    Optional<String> stringVariable(long taskKey, String name);

    void assign(long taskKey, String assignee);

    void unassign(long taskKey);

    /** Completes the task with exactly these variables. */
    void complete(long taskKey, Map<String, String> variables);

    /**
     * Everyone the engine records as assignee of the given element in this process instance, in any state:
     * completed, and also cancelled when its SLA timer expired while someone held it. Four eyes excludes them all.
     */
    Set<String> assigneesOf(long processInstanceKey, String elementId);
}
