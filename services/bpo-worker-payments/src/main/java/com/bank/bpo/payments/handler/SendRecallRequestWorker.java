package com.bank.bpo.payments.handler;

import com.bank.bpo.payments.port.RecallPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** psg.send-recall-request: asks the receiving bank, through the gateway, to return the payment. Key paymentId:recall-request. */
@Component
public class SendRecallRequestWorker {

    private static final Set<String> OUTPUTS = Set.of("recallRequestRef");
    private final RecallPort recalls;

    public SendRecallRequestWorker(RecallPort recalls) {
        this.recalls = recalls;
    }

    @JobWorker(type = "psg.send-recall-request", fetchVariables = {"rail", "paymentId"})
    public Map<String, Object> handle(@Variable String rail, @Variable String paymentId) {
        return JobErrors.guard(paymentId, () -> OutputVariables.of(OUTPUTS,
            Map.of("recallRequestRef", recalls.request(rail, paymentId, IdempotencyKeys.of(paymentId, "recall-request")))));
    }
}
