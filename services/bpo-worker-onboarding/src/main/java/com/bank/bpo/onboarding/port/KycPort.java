package com.bank.bpo.onboarding.port;

/** cus: the KYC status of an account application. */
public interface KycPort {

    enum Status { VERIFIED, PENDING, REJECTED }

    Status statusOf(String applicationId);
}
