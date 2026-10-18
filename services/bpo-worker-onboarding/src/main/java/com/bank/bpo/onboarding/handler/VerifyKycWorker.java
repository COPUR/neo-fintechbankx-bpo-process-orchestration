package com.bank.bpo.onboarding.handler;

import com.bank.bpo.onboarding.port.KycPort;
import com.bank.bpo.workerkit.JobErrors;
import com.bank.bpo.workerkit.OutputVariables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** cus.verify-kyc: reads the KYC status of the application; read-only, so it needs no idempotency key. */
@Component
public class VerifyKycWorker {

    private static final Set<String> OUTPUTS = Set.of("kycStatus");
    private final KycPort kyc;

    public VerifyKycWorker(KycPort kyc) {
        this.kyc = kyc;
    }

    @JobWorker(type = "cus.verify-kyc", fetchVariables = {"applicationId"})
    public Map<String, Object> handle(@Variable String applicationId) {
        return JobErrors.guard(applicationId, () -> OutputVariables.of(OUTPUTS, Map.of("kycStatus", kyc.statusOf(applicationId).name())));
    }
}
