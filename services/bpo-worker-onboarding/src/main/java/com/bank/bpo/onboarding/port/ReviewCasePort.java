package com.bank.bpo.onboarding.port;

/** cmp: the human review case behind a REVIEW screening. */
public interface ReviewCasePort {

    enum Outcome { CLEARED, REJECTED }

    String open(String screeningId, String sourceRef, String idempotencyKey);

    /** Posts the reviewer's decision (CLEAR, REJECT, ESCALATE or EXPIRED) and returns cmp's recorded outcome. */
    Outcome record(String caseId, String decision, String idempotencyKey);
}
