package com.bank.bpo.onboarding.port;

/** ntf: a transactional notification. Consent and quiet hours are ntf's rules. */
public interface NotificationPort {

    String notify(String applicationId, String purpose, String idempotencyKey);
}
