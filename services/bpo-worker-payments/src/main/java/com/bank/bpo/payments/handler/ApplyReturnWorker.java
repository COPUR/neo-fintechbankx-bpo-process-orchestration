package com.bank.bpo.payments.handler;

import com.bank.bpo.payments.port.PaymentRecallPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** pay.apply-return: the pivot of P6; pay credits the customer from the matched return. Key paymentId:apply-return. */
@Component
public class ApplyReturnWorker {

    private static final Set<String> OUTPUTS = Set.of("journalRef");
    private final PaymentRecallPort recalls;

    public ApplyReturnWorker(PaymentRecallPort recalls) {
        this.recalls = recalls;
    }

    @JobWorker(type = "pay.apply-return", fetchVariables = {"paymentId"})
    public Map<String, Object> handle(@Variable String paymentId) {
        return JobErrors.guard(paymentId, () -> OutputVariables.of(OUTPUTS,
            Map.of("journalRef", recalls.applyReturn(paymentId, IdempotencyKeys.of(paymentId, "apply-return")))));
    }
}
