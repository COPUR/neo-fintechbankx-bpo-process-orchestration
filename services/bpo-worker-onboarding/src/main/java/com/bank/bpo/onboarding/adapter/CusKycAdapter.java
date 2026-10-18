package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.KycPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Set;

/** cus GET /api/v1/applications/{applicationId}/kyc (contracts/expectations/cus.yaml). */
public final class CusKycAdapter implements KycPort {

    private static final Set<String> STATUSES = Set.of("VERIFIED", "PENDING", "REJECTED");
    private final ProviderHttp http;

    public CusKycAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public Status statusOf(String applicationId) {
        String id = Ids.require("applicationId", applicationId);
        return Status.valueOf(Ids.oneOf("kycStatus", http.get("/api/v1/applications/" + id + "/kyc").get("kycStatus"), STATUSES));
    }
}
