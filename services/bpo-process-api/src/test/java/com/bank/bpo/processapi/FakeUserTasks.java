package com.bank.bpo.processapi;

import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.domain.EngineTask;
import com.bank.bpo.processapi.domain.TaskRefusal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An in-memory engine: tasks by key, their variables, and a log of every write the API made. */
public final class FakeUserTasks implements UserTaskPort {

    public final Map<Long, EngineTask> tasks = new LinkedHashMap<>();
    public final Map<Long, Map<String, String>> variables = new HashMap<>();
    public final List<String> writes = new ArrayList<>();
    public final Map<Long, Map<String, String>> completions = new HashMap<>();
    public boolean down;

    public EngineTask add(long key, String process, String element, String assignee, long instance, String... groups) {
        EngineTask task = new EngineTask(key, process, element, EngineTask.OPEN, assignee, List.of(groups), instance,
            Instant.parse("2026-10-18T10:00:00Z").plusSeconds(key), Instant.parse("2026-10-18T14:00:00Z").minusSeconds(key));
        tasks.put(key, task);
        return task;
    }

    @Override
    public List<EngineTask> openTasks(String processDefinitionId, String elementId, Set<String> groups, int limit) {
        check();
        return tasks.values().stream()
            .filter(t -> t.open() && t.processDefinitionId().equals(processDefinitionId) && t.elementId().equals(elementId))
            .filter(t -> t.candidateGroups().stream().anyMatch(groups::contains))
            .limit(limit).toList();
    }

    @Override
    public Optional<EngineTask> find(long taskKey) {
        check();
        return Optional.ofNullable(tasks.get(taskKey));
    }

    @Override
    public Optional<String> stringVariable(long taskKey, String name) {
        check();
        return Optional.ofNullable(variables.getOrDefault(taskKey, Map.of()).get(name));
    }

    @Override
    public void assign(long taskKey, String assignee) {
        check();
        writes.add("assign " + taskKey + " " + assignee);
        replace(taskKey, assignee, EngineTask.OPEN);
    }

    @Override
    public void unassign(long taskKey) {
        check();
        writes.add("unassign " + taskKey);
        replace(taskKey, null, EngineTask.OPEN);
    }

    @Override
    public void complete(long taskKey, Map<String, String> vars) {
        check();
        writes.add("complete " + taskKey + " " + vars);
        completions.put(taskKey, vars);
        replace(taskKey, tasks.get(taskKey).assignee(), "COMPLETED");
    }

    @Override
    public Set<String> completedBy(long processInstanceKey, String elementId) {
        check();
        Set<String> who = new LinkedHashSet<>();
        tasks.values().stream()
            .filter(t -> t.processInstanceKey() == processInstanceKey && t.elementId().equals(elementId)
                && "COMPLETED".equals(t.state()) && t.assignee() != null)
            .forEach(t -> who.add(t.assignee()));
        return who;
    }

    private void replace(long key, String assignee, String state) {
        EngineTask t = tasks.get(key);
        tasks.put(key, new EngineTask(t.key(), t.processDefinitionId(), t.elementId(), state, assignee,
            t.candidateGroups(), t.processInstanceKey(), t.creationDate(), t.dueDate()));
    }

    private void check() {
        if (down) {
            throw new TaskRefusal(TaskRefusal.Reason.ENGINE_UNAVAILABLE);
        }
    }
}
