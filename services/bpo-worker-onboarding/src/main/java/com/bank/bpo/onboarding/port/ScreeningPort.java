package com.bank.bpo.onboarding.port;

/** cmp: screening of the applicant. The decision of record is cmp's; the process only routes on it. */
public interface ScreeningPort {

    enum Decision { CLEAR, REVIEW, REJECT }

    record Result(String screeningId, Decision decision) { }

    Result screen(String applicationId, String idempotencyKey);
}
