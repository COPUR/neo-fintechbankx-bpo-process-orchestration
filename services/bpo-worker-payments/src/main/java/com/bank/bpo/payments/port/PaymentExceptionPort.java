package com.bank.bpo.payments.port;

/** pay: the payment while it is EXCEPTION_OWNED_BY_BPO. The outcome of record is pay's. */
public interface PaymentExceptionPort {

    enum Outcome { SETTLE, RELEASE, HANDED_BACK }

    /**
     * Posts the operations decision (SETTLED, REJECTED, UNRESOLVED or EXPIRED) with its coded reason; pay checks it
     * against the gateway's record. UNRESOLVED and EXPIRED only ever come back HANDED_BACK.
     */
    Outcome record(String paymentId, String decision, String reason, String idempotencyKey);

    void markSettled(String paymentId, String journalRef, String idempotencyKey);

    void markRejected(String paymentId, String idempotencyKey);
}
