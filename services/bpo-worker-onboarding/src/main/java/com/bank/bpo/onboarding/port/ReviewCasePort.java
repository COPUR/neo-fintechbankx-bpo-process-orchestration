package com.bank.bpo.onboarding.port;

/** cmp: the human review case behind a REVIEW screening. */
public interface ReviewCasePort {

    enum Outcome { CLEARED, REJECTED }

    /** sourceProcess and jurisdiction are cmp's routing inputs for the case (the same ones cmp-review-routing reads). */
    String open(String screeningId, String sourceRef, String sourceProcess, String jurisdiction, String idempotencyKey);

    /** Posts the reviewer's decision (CLEAR, REJECT or EXPIRED) with its coded reason and returns cmp's recorded outcome. */
    Outcome record(String caseId, String decision, String reason, String idempotencyKey);
}
