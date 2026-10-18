package com.bank.bpo.processapi.application.port;

import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.TaskRefusal;
import java.time.Duration;

/**
 * What the staff screens are measured by (decision time, SLA misses, four-eyes and other refusals). Events carry
 * the actor, the task type and enums only: never a task key, a business id or a user name.
 */
public interface TaskEvents {

    void claimed(Actor actor, String taskType);

    void released(Actor actor, String taskType);

    /** A decision was recorded; {@code age} runs from task creation, {@code late} is true past the due date. */
    void decided(Actor actor, String taskType, String decision, Duration age, boolean late);

    void refused(Actor actor, TaskRefusal.Reason reason);

    TaskEvents NONE = new TaskEvents() {
        @Override
        public void claimed(Actor actor, String taskType) {
        }

        @Override
        public void released(Actor actor, String taskType) {
        }

        @Override
        public void decided(Actor actor, String taskType, String decision, Duration age, boolean late) {
        }

        @Override
        public void refused(Actor actor, TaskRefusal.Reason reason) {
        }
    };
}
