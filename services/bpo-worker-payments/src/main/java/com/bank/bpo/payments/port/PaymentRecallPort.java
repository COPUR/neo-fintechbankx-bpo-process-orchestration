package com.bank.bpo.payments.port;

/** pay: the recall case of a payment (P6). Matching a return to the original and crediting the customer is pay's. */
public interface PaymentRecallPort {

    enum Outcome { RETURNED, UNRESOLVED }

    /**
     * Asks pay to apply the return the gateway reported: pay matches it to the original payment (never more than
     * the original amount) and credits the customer. Returns the journal entry; a replay with the same key returns
     * the same entry. A return pay cannot match is a business rejection (RETURN_NOT_MATCHED).
     */
    String applyReturn(String paymentId, String idempotencyKey);

    void close(String paymentId, Outcome outcome, String idempotencyKey);
}
