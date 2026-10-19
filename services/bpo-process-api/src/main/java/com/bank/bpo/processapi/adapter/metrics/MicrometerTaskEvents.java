package com.bank.bpo.processapi.adapter.metrics;

import com.bank.bpo.processapi.application.port.TaskEvents;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.TaskRefusal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Task events as Micrometer meters. Tags are bounded enums (actor, task type, decision, reason, sla), so the
 * series count is fixed by the task catalogue; no key, id or user name is ever a tag.
 *
 * <ul>
 *   <li>{@code bpo.staff.task.claims} and {@code bpo.staff.task.releases} by actor and task type</li>
 *   <li>{@code bpo.staff.task.decisions} by actor, task type, decision and sla (on-time, late)</li>
 *   <li>{@code bpo.staff.task.age} timer from task creation to decision, same tags without decision</li>
 *   <li>{@code bpo.staff.task.refusals} by actor and reason (FOUR_EYES_REQUIRED, TASK_CLAIMED_BY_OTHER, ...)</li>
 * </ul>
 */
public final class MicrometerTaskEvents implements TaskEvents {

    private final MeterRegistry registry;

    public MicrometerTaskEvents(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void claimed(Actor actor, String taskType) {
        Counter.builder("bpo.staff.task.claims").tag("actor", actor.id()).tag("taskType", taskType).register(registry).increment();
    }

    @Override
    public void released(Actor actor, String taskType) {
        Counter.builder("bpo.staff.task.releases").tag("actor", actor.id()).tag("taskType", taskType).register(registry).increment();
    }

    @Override
    public void decided(Actor actor, String taskType, String decision, Duration age, boolean late) {
        String sla = late ? "late" : "on-time";
        Counter.builder("bpo.staff.task.decisions").tag("actor", actor.id()).tag("taskType", taskType)
            .tag("decision", decision).tag("sla", sla).register(registry).increment();
        if (age != null && !age.isNegative()) {
            Timer.builder("bpo.staff.task.age").tag("actor", actor.id()).tag("taskType", taskType).tag("sla", sla)
                .register(registry).record(age);
        }
    }

    @Override
    public void refused(Actor actor, TaskRefusal.Reason reason) {
        Counter.builder("bpo.staff.task.refusals").tag("actor", actor.id()).tag("reason", reason.name()).register(registry).increment();
    }
}
