package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.NotificationPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** ntf.notify: a transactional notification for a purpose enum. One key per application and purpose. */
@Component
public class NotifyWorker {

    private static final Set<String> OUTPUTS = Set.of("notificationRef");
    private final NotificationPort notifications;

    public NotifyWorker(NotificationPort notifications) {
        this.notifications = notifications;
    }

    @JobWorker(type = "ntf.notify", fetchVariables = {"applicationId", "purpose"})
    public Map<String, Object> handle(@Variable String applicationId, @Variable String purpose) {
        String step = "notify-" + purpose.toLowerCase(Locale.ROOT).replace('_', '-');
        return JobErrors.guard(() -> OutputVariables.of(OUTPUTS,
            Map.of("notificationRef", notifications.notify(applicationId, purpose, IdempotencyKeys.of(applicationId, step)))));
    }
}
