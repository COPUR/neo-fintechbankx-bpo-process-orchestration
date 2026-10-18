package com.bank.bpo.processapi.application;

import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.BODY_NOT_ALLOWED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.DECISION_NOT_ALLOWED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.FOUR_EYES_REQUIRED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.NOT_YOUR_ACTOR;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_CLAIMED_BY_OTHER;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_NOT_ASSIGNED_TO_YOU;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_NOT_FOUND;

import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.EngineTask;
import com.bank.bpo.processapi.domain.StaffUser;
import com.bank.bpo.processapi.domain.TaskRefusal;
import com.bank.bpo.processapi.domain.TaskType;
import com.bank.bpo.processapi.domain.TaskTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The task use cases of every actor: list, read, claim, release and decide. Every operation re-reads the task
 * from the engine and checks, in this order: the user acts as the actor, the task is open, of a kind this actor
 * works and in one of the user's queues. Anything else is TASK_NOT_FOUND, so a key outside the user's queues
 * reveals nothing. A decision writes one variable, the task type's decision variable, and nothing else.
 */
public final class TaskInbox {

    /** Engine page size per task type; the staff web pages within it. */
    static final int LIMIT = 200;
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final UserTaskPort tasks;
    private final QueueGroups queues;

    public TaskInbox(UserTaskPort tasks, QueueGroups queues) {
        this.tasks = tasks;
        this.queues = queues;
    }

    public List<Actor> actorsOf(StaffUser user) {
        return queues.actorsOf(user);
    }

    /** Open tasks of the actor in the user's queues, oldest due date first; tasks held by others are included. */
    public List<TaskView> list(Actor actor, StaffUser user) {
        Set<String> groups = groupsOrRefuse(actor, user);
        List<TaskView> views = new ArrayList<>();
        for (TaskType type : TaskTypes.of(actor)) {
            for (EngineTask task : tasks.openTasks(type.processDefinitionId(), type.elementId(), groups, LIMIT)) {
                if (visible(task, type, groups)) {
                    views.add(view(task, type, user, null));
                }
            }
        }
        views.sort(Comparator.comparing(TaskView::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskView::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return views;
    }

    /** One task with its read-only reference to the owning service's record. */
    public TaskView read(Actor actor, StaffUser user, long taskKey) {
        Visible v = visibleOrRefuse(actor, user, taskKey);
        String reference = tasks.stringVariable(taskKey, v.type.referenceVariable())
            .filter(r -> REFERENCE.matcher(r).matches())
            .orElse(null);
        return view(v.task, v.type, user, reference);
    }

    public TaskView claim(Actor actor, StaffUser user, long taskKey) {
        Visible v = visibleOrRefuse(actor, user, taskKey);
        if (v.task.assignee() == null) {
            tasks.assign(taskKey, user.id());
        } else if (!user.id().equals(v.task.assignee())) {
            throw new TaskRefusal(TASK_CLAIMED_BY_OTHER);
        }
        return read(actor, user, taskKey);
    }

    public void release(Actor actor, StaffUser user, long taskKey) {
        Visible v = visibleOrRefuse(actor, user, taskKey);
        if (!user.id().equals(v.task.assignee())) {
            throw new TaskRefusal(TASK_NOT_ASSIGNED_TO_YOU);
        }
        tasks.unassign(taskKey);
    }

    /**
     * Records the user's decision. {@code body} is the request body as sent; it must hold the field
     * {@code decision} and nothing else, so no caller can write an id or any other variable.
     */
    public void decide(Actor actor, StaffUser user, long taskKey, Map<String, ?> body) {
        if (body == null || body.size() != 1 || !(body.get("decision") instanceof String decision)) {
            throw new TaskRefusal(BODY_NOT_ALLOWED);
        }
        Visible v = visibleOrRefuse(actor, user, taskKey);
        if (!user.id().equals(v.task.assignee())) {
            throw new TaskRefusal(TASK_NOT_ASSIGNED_TO_YOU);
        }
        if (!v.type.allows(decision)) {
            throw new TaskRefusal(DECISION_NOT_ALLOWED);
        }
        if (v.type.maker().isPresent()
            && tasks.completedBy(v.task.processInstanceKey(), v.type.maker().get()).contains(user.id())) {
            throw new TaskRefusal(FOUR_EYES_REQUIRED);
        }
        tasks.complete(taskKey, Map.of(v.type.decisionVariable(), decision));
    }

    private Set<String> groupsOrRefuse(Actor actor, StaffUser user) {
        Set<String> groups = queues.of(actor, user);
        if (groups.isEmpty()) {
            throw new TaskRefusal(NOT_YOUR_ACTOR);
        }
        return groups;
    }

    private Visible visibleOrRefuse(Actor actor, StaffUser user, long taskKey) {
        Set<String> groups = groupsOrRefuse(actor, user);
        EngineTask task = tasks.find(taskKey).orElseThrow(() -> new TaskRefusal(TASK_NOT_FOUND));
        TaskType type = TaskTypes.find(task.processDefinitionId(), task.elementId())
            .filter(t -> t.actor() == actor)
            .orElseThrow(() -> new TaskRefusal(TASK_NOT_FOUND));
        if (!visible(task, type, groups)) {
            throw new TaskRefusal(TASK_NOT_FOUND);
        }
        return new Visible(task, type);
    }

    private static boolean visible(EngineTask task, TaskType type, Set<String> groups) {
        return task.open()
            && Objects.equals(task.processDefinitionId(), type.processDefinitionId())
            && Objects.equals(task.elementId(), type.elementId())
            && task.candidateGroups().stream().anyMatch(groups::contains);
    }

    private static TaskView view(EngineTask task, TaskType type, StaffUser user, String reference) {
        TaskView.Assignment assignment = task.assignee() == null ? TaskView.Assignment.UNASSIGNED
            : user.id().equals(task.assignee()) ? TaskView.Assignment.MINE : TaskView.Assignment.OTHER;
        return new TaskView(task.key(), type.actor(), type.elementId(), type.processDefinitionId(), assignment,
            task.creationDate(), task.dueDate(), type.maker().isPresent(), type.decisions(),
            type.referenceVariable(), reference);
    }

    private record Visible(EngineTask task, TaskType type) {
    }
}
