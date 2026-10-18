package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.AliasOfferPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** psg-tr POST /api/v1/psg-tr/alias-offers (contracts/expectations/psg-tr.yaml). */
public final class PsgTrAliasAdapter implements AliasOfferPort {

    private static final Set<String> STATUSES = Set.of("OFFERED", "NOT_ELIGIBLE");
    private final ProviderHttp http;

    public PsgTrAliasAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String offer(String applicationId, String accountId, String idempotencyKey) {
        Map<String, Object> request = Map.of("applicationId", Ids.require("applicationId", applicationId),
            "accountId", Ids.require("accountId", accountId));
        return Ids.oneOf("status", http.post("/api/v1/psg-tr/alias-offers", request, idempotencyKey).get("status"), STATUSES);
    }
}
