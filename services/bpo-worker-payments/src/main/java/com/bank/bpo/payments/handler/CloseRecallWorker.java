package com.bank.bpo.payments.handler;

import com.bank.bpo.payments.port.PaymentRecallPort;
import com.bank.bpo.payments.port.PaymentRecallPort.Outcome;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.JobErrors;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** pay.close-recall: closes the recall case with RETURNED or UNRESOLVED. Key paymentId:close-recall. */
@Component
public class CloseRecallWorker {

    private static final Set<String> OUTCOMES = Set.of("RETURNED", "UNRESOLVED");
    private final PaymentRecallPort recalls;

    public CloseRecallWorker(PaymentRecallPort recalls) {
        this.recalls = recalls;
    }

    @JobWorker(type = "pay.close-recall", fetchVariables = {"paymentId", "recallOutcome"})
    public Map<String, Object> handle(@Variable String paymentId, @Variable String recallOutcome) {
        JobErrors.guard(paymentId, () -> {
            Outcome outcome = Outcome.valueOf(Ids.oneOf("recallOutcome", recallOutcome, OUTCOMES));
            recalls.close(paymentId, outcome, IdempotencyKeys.of(paymentId, "close-recall"));
            return null;
        });
        return Map.of();
    }
}
