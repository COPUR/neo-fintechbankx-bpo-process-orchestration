package com.bank.bpo.onboarding.port;

/** ntf: a transactional notification. Consent and quiet hours are ntf's rules. */
public interface NotificationPort {

    String notify(String subjectRef, String purpose, String idempotencyKey);
}
