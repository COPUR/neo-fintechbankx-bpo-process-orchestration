package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.AccountApplicationPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.springframework.stereotype.Component;

/** acc.reject-application: closes the application with a reason code (never free text). */
@Component
public class RejectApplicationWorker {

    private final AccountApplicationPort accounts;

    public RejectApplicationWorker(AccountApplicationPort accounts) {
        this.accounts = accounts;
    }

    @JobWorker(type = "acc.reject-application", fetchVariables = {"applicationId", "rejectionReason"})
    public Map<String, Object> handle(@Variable String applicationId, @Variable String rejectionReason) {
        JobErrors.guard(applicationId, () -> {
            accounts.reject(applicationId, rejectionReason, IdempotencyKeys.of(applicationId, "reject"));
            return null;
        });
        return Map.of();
    }
}
