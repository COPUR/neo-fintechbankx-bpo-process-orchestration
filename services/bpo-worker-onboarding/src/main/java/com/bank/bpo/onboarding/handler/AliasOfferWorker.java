package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.AliasOfferPort;
import com.bank.bpo.workerkit.IdempotencyKeys;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** psg-tr.alias-offer: offers the Kolay Adres alias for the new account (TR only). */
@Component
public class AliasOfferWorker {

    private static final Set<String> OUTPUTS = Set.of("aliasOfferStatus");
    private final AliasOfferPort aliases;

    public AliasOfferWorker(AliasOfferPort aliases) {
        this.aliases = aliases;
    }

    @JobWorker(type = "psg-tr.alias-offer", fetchVariables = {"applicationId", "accountId"})
    public Map<String, Object> handle(@Variable String applicationId, @Variable String accountId) {
        return JobErrors.guard(applicationId, () -> OutputVariables.of(OUTPUTS,
            Map.of("aliasOfferStatus", aliases.offer(applicationId, accountId, IdempotencyKeys.of(applicationId, "alias-offer")))));
    }
}
