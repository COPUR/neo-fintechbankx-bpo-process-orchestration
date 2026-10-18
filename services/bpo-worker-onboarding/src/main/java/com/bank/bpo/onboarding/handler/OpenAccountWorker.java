package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.AccountApplicationPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** acc.open-account: the pivot step of P1. The key is applicationId:open, so a retry or a restart replays. */
@Component
public class OpenAccountWorker {

    private static final Set<String> OUTPUTS = Set.of("accountId");
    private final AccountApplicationPort accounts;

    public OpenAccountWorker(AccountApplicationPort accounts) {
        this.accounts = accounts;
    }

    @JobWorker(type = "acc.open-account", fetchVariables = {"applicationId"})
    public Map<String, Object> handle(@Variable String applicationId) {
        return JobErrors.guard(() -> OutputVariables.of(OUTPUTS,
            Map.of("accountId", accounts.open(applicationId, IdempotencyKeys.of(applicationId, "open")))));
    }
}
