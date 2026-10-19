package com.bank.bpo.processapi.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.TaskRefusal;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MicrometerTaskEventsTest {

    @Test
    void metersCarryOnlyBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTaskEvents events = new MicrometerTaskEvents(registry);

        events.claimed(Actor.PAYMENTS_OPERATIONS, "Task_OpsReview");
        events.decided(Actor.PAYMENTS_OPERATIONS, "Task_OpsReview", "SETTLED", Duration.ofMinutes(12), false);
        events.decided(Actor.PAYMENTS_SUPERVISOR, "Task_SupervisorReview", "REJECTED", Duration.ofHours(3), true);
        events.refused(Actor.PAYMENTS_SUPERVISOR, TaskRefusal.Reason.FOUR_EYES_REQUIRED);

        assertThat(registry.get("bpo.staff.task.decisions").tags("actor", "payments-operations", "decision", "SETTLED", "sla", "on-time")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("bpo.staff.task.age").tags("sla", "late").timer().totalTime(java.util.concurrent.TimeUnit.HOURS)).isEqualTo(3);
        assertThat(registry.get("bpo.staff.task.refusals").tag("reason", "FOUR_EYES_REQUIRED").counter().count()).isEqualTo(1);
        Set<String> keys = registry.getMeters().stream().map(Meter::getId).flatMap(id -> id.getTags().stream())
            .map(Tag::getKey).collect(Collectors.toSet());
        assertThat(keys).containsOnly("actor", "taskType", "decision", "sla", "reason");
    }
}
