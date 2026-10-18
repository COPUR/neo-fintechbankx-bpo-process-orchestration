package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.AccountApplicationPort;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

public final class AccApplicationAdapter implements AccountApplicationPort {

    private final ProviderHttp http;

    public AccApplicationAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String open(String applicationId, String idempotencyKey) {
        return String.valueOf(http.post("/api/v1/account-applications/" + applicationId + "/open", Map.of(), idempotencyKey).get("accountId"));
    }

    @Override
    public void reject(String applicationId, String reason, String idempotencyKey) {
        http.post("/api/v1/account-applications/" + applicationId + "/reject", Map.of("reason", reason), idempotencyKey);
    }
}
