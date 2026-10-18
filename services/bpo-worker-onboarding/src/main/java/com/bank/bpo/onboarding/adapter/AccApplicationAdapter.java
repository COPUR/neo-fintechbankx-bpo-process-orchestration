package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.AccountApplicationPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** acc account applications: open (the pivot) and reject (contracts/expectations/acc.yaml). */
public final class AccApplicationAdapter implements AccountApplicationPort {

    private static final Set<String> REASONS = Set.of("KYC_REJECTED", "KYC_TIMEOUT", "SCREENING_REJECTED");
    private final ProviderHttp http;

    public AccApplicationAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String open(String applicationId, String idempotencyKey) {
        String id = Ids.require("applicationId", applicationId);
        // No accountId in a 2xx is a contract fault: the instance stops on an incident instead of telling the
        // applicant an account exists.
        return Ids.require("accountId", http.post("/api/v1/account-applications/" + id + "/open", Map.of(), idempotencyKey).get("accountId"));
    }

    @Override
    public void reject(String applicationId, String reason, String idempotencyKey) {
        String id = Ids.require("applicationId", applicationId);
        http.post("/api/v1/account-applications/" + id + "/reject",
            Map.of("rejectionReason", Ids.oneOf("rejectionReason", reason, REASONS)), idempotencyKey);
    }
}
