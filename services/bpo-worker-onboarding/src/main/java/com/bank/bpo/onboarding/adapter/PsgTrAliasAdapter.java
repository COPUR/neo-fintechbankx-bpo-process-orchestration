package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.AliasOfferPort;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

public final class PsgTrAliasAdapter implements AliasOfferPort {

    private final ProviderHttp http;

    public PsgTrAliasAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String offer(String accountId, String idempotencyKey) {
        return String.valueOf(http.post("/api/v1/psg-tr/alias-offers", Map.of("accountId", accountId), idempotencyKey).get("status"));
    }
}
