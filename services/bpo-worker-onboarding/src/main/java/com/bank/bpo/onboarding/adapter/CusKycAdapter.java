package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.KycPort;
import com.bank.bpo.workerkit.ProviderHttp;

public final class CusKycAdapter implements KycPort {

    private final ProviderHttp http;

    public CusKycAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public Status statusOf(String applicationId) {
        return Status.valueOf(String.valueOf(http.get("/api/v1/applications/" + applicationId + "/kyc").get("kycStatus")));
    }
}
