package com.bank.bpo.payments.handler;

import com.bank.bpo.payments.port.RecallPort;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** psg.recall-status: reads the gateway's record of the recall answer; read-only, so it needs no idempotency key. */
@Component
public class RecallStatusWorker {

    private static final Set<String> OUTPUTS = Set.of("recallStatus");
    private final RecallPort recalls;

    public RecallStatusWorker(RecallPort recalls) {
        this.recalls = recalls;
    }

    @JobWorker(type = "psg.recall-status", fetchVariables = {"rail", "paymentId"})
    public Map<String, Object> handle(@Variable String rail, @Variable String paymentId) {
        return JobErrors.guard(paymentId, () -> OutputVariables.of(OUTPUTS,
            Map.of("recallStatus", recalls.statusOf(rail, paymentId).name())));
    }
}
