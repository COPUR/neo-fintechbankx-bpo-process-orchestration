package com.bank.bpo.onboarding.port;

/** psg-tr: the Kolay Adres alias offer for a new account. */
public interface AliasOfferPort {

    String offer(String applicationId, String accountId, String idempotencyKey);
}
