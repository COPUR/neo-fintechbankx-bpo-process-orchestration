package com.bank.bpo.onboarding.port;

/** acc: the account application. Opening is the pivot step of P1 (05 section 7). */
public interface AccountApplicationPort {

    String open(String applicationId, String idempotencyKey);

    void reject(String applicationId, String reason, String idempotencyKey);
}
