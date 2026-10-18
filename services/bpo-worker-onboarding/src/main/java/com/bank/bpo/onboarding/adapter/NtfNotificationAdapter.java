package com.bank.bpo.onboarding.adapter;

import com.bank.bpo.onboarding.port.NotificationPort;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

public final class NtfNotificationAdapter implements NotificationPort {

    private final ProviderHttp http;

    public NtfNotificationAdapter(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public String notify(String applicationId, String purpose, String idempotencyKey) {
        return String.valueOf(http.post("/api/v1/notifications",
            Map.of("applicationId", applicationId, "purpose", purpose), idempotencyKey).get("notificationRef"));
    }
}
