package com.bank.bpo.processapi.application;

import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.BODY_NOT_ALLOWED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.DECISION_NOT_ALLOWED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.ENGINE_UNAVAILABLE;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.FOUR_EYES_REQUIRED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.NOT_YOUR_ACTOR;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.REASON_NOT_ALLOWED;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_CLAIMED_BY_OTHER;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_NOT_ASSIGNED_TO_YOU;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_NOT_FOUND;

import com.bank.bpo.processapi.application.port.TaskEvents;
import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.EngineTask;
import com.bank.bpo.processapi.domain.StaffUser;
import com.bank.bpo.processapi.domain.TaskRefusal;
import com.bank.bpo.processapi.domain.TaskType;
import com.bank.bpo.processapi.domain.TaskTypes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The task use cases of every actor: list, read, claim, release and decide. Every operation re-reads the task
 * from the engine and checks, in this order: the user acts as the actor, the task is open, of a kind this actor
 * works and in one of the user's queues. Anything else is TASK_NOT_FOUND, so a key outside the user's queues
 * reveals nothing. A decision writes two variables, the task type's decision and its coded reason, and nothing
 * else.
 * Lists come a page at a time ({@link #page}).
 * Claims, releases, decisions and refusals are reported to {@link TaskEvents} for the screens' measures.
 */
public final class TaskInbox {

    /** Engine page size per task type; the staff web pages within it. */
    static final int LIMIT = 200;
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** List order: due date, then creation date (missing ones last), then task key, so every position is unique. */
    private static final Comparator<TaskQuery.Position> ORDER =
        Comparator.comparing(TaskQuery.Position::due, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(TaskQuery.Position::created, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparingLong(TaskQuery.Position::key);

    private final UserTaskPort tasks;
    private final QueueGroups queues;
    private final TaskEvents events;
    private final Clock clock;

    public TaskInbox(UserTaskPort tasks, QueueGroups queues) {
        this(tasks, queues, TaskEvents.NONE, Clock.systemUTC());
    }

    public TaskInbox(UserTaskPort tasks, QueueGroups queues, TaskEvents events, Clock clock) {
        this.tasks = tasks;
        this.queues = queues;
        this.events = events;
        this.clock = clock;
    }

    public List<Actor> actorsOf(StaffUser user) {
        return queues.actorsOf(user);
    }

    /** Open tasks of the actor in the user's queues, oldest due date first; tasks held by others are included. */
    public List<TaskView> list(Actor actor, StaffUser user) {
        return reported(actor, () -> listChecked(actor, user));
    }

    private List<TaskView> listChecked(Actor actor, StaffUser user) {
        Set<String> groups = groupsOrRefuse(actor, user);
        List<TaskView> views = new ArrayList<>();
        for (TaskType type : TaskTypes.of(actor)) {
            for (EngineTask task : tasks.openTasks(type.processDefinitionId(), type.elementId(), groups, LIMIT)) {
                if (visible(task, type, groups)) {
                    views.add(view(task, type, user, null, false));
                }
            }
        }
        views.sort(Comparator.comparing(TaskQuery.Position::of, ORDER));
        return views;
    }

    /**
     * One page of the actor's list. The whole visible list is read from the engine (at most {@link #LIMIT} tasks
     * per task type), counted, filtered and cut after the cursor, so pages never overlap or skip a task that
     * stays in the list.
     */
    public TaskPage page(Actor actor, StaffUser user, TaskQuery query) {
        return reported(actor, () -> {
            Instant asOf = clock.instant();
            List<TaskView> all = listChecked(actor, user).stream()
                .filter(v -> query.createdAfter() == null
                    || v.createdAt() != null && v.createdAt().isAfter(query.createdAfter()))
                .toList();
            TaskPage.Counts counts = new TaskPage.Counts(
                (int) all.stream().filter(TaskQuery.Filter.MINE::keeps).count(),
                (int) all.stream().filter(TaskQuery.Filter.OPEN::keeps).count(), all.size());
            List<TaskView> rest = all.stream()
                .filter(query.filter()::keeps)
                .filter(v -> query.after() == null || ORDER.compare(TaskQuery.Position.of(v), query.after()) > 0)
                .toList();
            List<TaskView> items = rest.subList(0, Math.min(query.limit(), rest.size()));
            String next = rest.size() > items.size() ? TaskQuery.Position.of(items.getLast()).encode() : null;
            return new TaskPage(items, next, counts, asOf);
        });
    }

    /** One task with its read-only reference to the owning service's record. */
    public TaskView read(Actor actor, StaffUser user, long taskKey) {
        return reported(actor, () -> readChecked(actor, user, taskKey));
    }

    private TaskView readChecked(Actor actor, StaffUser user, long taskKey) {
        Visible v = visibleOrRefuse(actor, user, taskKey);
        String reference = tasks.stringVariable(taskKey, v.type.referenceVariable())
            .filter(r -> REFERENCE.matcher(r).matches())
            .orElse(null);
        return view(v.task, v.type, user, reference, heldMaker(v, user));
    }

    public TaskView claim(Actor actor, StaffUser user, long taskKey) {
        return reported(actor, () -> {
            Visible v = visibleOrRefuse(actor, user, taskKey);
            if (v.task.assignee() == null) {
                // A user four eyes keeps from deciding cannot hold the task either, or it would sit until expiry.
                if (heldMaker(v, user)) {
                    throw new TaskRefusal(FOUR_EYES_REQUIRED);
                }
                tasks.assign(taskKey, user.id());
                events.claimed(actor, v.type.elementId());
                try {
                    return readChecked(actor, user, taskKey);
                } catch (TaskRefusal readFailed) {
                    // The claim went through and only the engine stopped answering: say what is known instead of a
                    // refusal that would read as "nothing recorded"; the screen reads the task again anyway. Any
                    // other refusal (the task closed meanwhile) is the true state and goes back as it is.
                    if (readFailed.reason() != ENGINE_UNAVAILABLE) {
                        throw readFailed;
                    }
                    return view(v.task, v.type, null, false, TaskView.Assignment.MINE);
                }
            } else if (!user.id().equals(v.task.assignee())) {
                throw new TaskRefusal(TASK_CLAIMED_BY_OTHER);
            }
            return readChecked(actor, user, taskKey);
        });
    }

    public void release(Actor actor, StaffUser user, long taskKey) {
        reported(actor, () -> {
            Visible v = visibleOrRefuse(actor, user, taskKey);
            if (!user.id().equals(v.task.assignee())) {
                throw new TaskRefusal(TASK_NOT_ASSIGNED_TO_YOU);
            }
            tasks.unassign(taskKey);
            events.released(actor, v.type.elementId());
            return null;
        });
    }

    /**
     * Records the user's decision. {@code body} is the request body as sent; it must hold the fields
     * {@code decision} and {@code reason} and nothing else, so no caller can write an id, free text or any other
     * variable. The reason is a code from the task type's human list (U4).
     */
    public void decide(Actor actor, StaffUser user, long taskKey, Map<String, ?> body) {
        reported(actor, () -> {
            if (body == null || body.size() != 2 || !(body.get("decision") instanceof String decision)
                || !(body.get("reason") instanceof String reason)) {
                throw new TaskRefusal(BODY_NOT_ALLOWED);
            }
            Visible v = visibleOrRefuse(actor, user, taskKey);
            if (!user.id().equals(v.task.assignee())) {
                throw new TaskRefusal(TASK_NOT_ASSIGNED_TO_YOU);
            }
            if (!v.type.allows(decision)) {
                throw new TaskRefusal(DECISION_NOT_ALLOWED);
            }
            if (!v.type.allowsReason(reason)) {
                throw new TaskRefusal(REASON_NOT_ALLOWED);
            }
            // Four eyes: the maker task's last assignee, whether it completed or expired, cannot decide the checker
            // task. The engine keeps no earlier assignees; the identity separation-of-duties rule covers those. A maker
            // task recorded under the user's former name (before the assignee was the token subject) counts too.
            if (heldMaker(v, user)) {
                throw new TaskRefusal(FOUR_EYES_REQUIRED);
            }
            tasks.complete(taskKey, Map.of(v.type.decisionVariable(), decision, v.type.reasonVariable(), reason));
            Instant now = clock.instant();
            events.decided(actor, v.type.elementId(), decision,
                v.task.creationDate() == null ? null : Duration.between(v.task.creationDate(), now),
                v.task.dueDate() != null && now.isAfter(v.task.dueDate()));
            return null;
        });
    }

    private boolean heldMaker(Visible v, StaffUser user) {
        return v.type.maker().isPresent()
            && tasks.assigneesOf(v.task.processInstanceKey(), v.type.maker().get()).stream().anyMatch(user::mayBe);
    }

    private <T> T reported(Actor actor, Supplier<T> work) {
        try {
            return work.get();
        } catch (TaskRefusal refusal) {
            events.refused(actor, refusal.reason());
            throw refusal;
        }
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

    private static TaskView view(EngineTask task, TaskType type, StaffUser user, String reference,
                                 boolean fourEyesBlocked) {
        TaskView.Assignment assignment = task.assignee() == null ? TaskView.Assignment.UNASSIGNED
            : user.id().equals(task.assignee()) ? TaskView.Assignment.MINE : TaskView.Assignment.OTHER;
        return view(task, type, reference, fourEyesBlocked, assignment);
    }

    private static TaskView view(EngineTask task, TaskType type, String reference, boolean fourEyesBlocked,
                                 TaskView.Assignment assignment) {
        return new TaskView(task.key(), type.actor(), type.elementId(), type.processDefinitionId(), assignment,
            task.creationDate(), task.dueDate(), type.maker().isPresent(), type.decisions(),
            type.reasons(), type.referenceVariable(), reference, fourEyesBlocked);
    }

    private record Visible(EngineTask task, TaskType type) {
    }
}
