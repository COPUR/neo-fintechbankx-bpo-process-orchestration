package com.bank.bpo.payments.port;

/**
 * psg: a recall request for an instruction the scheme already settled (P6). The gateway picks the channel to the
 * receiving bank; sending the request moves no money. The status is the gateway's record of the answer.
 */
public interface RecallPort {

    enum Status { RETURNED, DECLINED, PENDING }

    /** Sends the request once per key and returns the gateway's reference for it. */
    String request(String rail, String paymentId, String idempotencyKey);

    Status statusOf(String rail, String paymentId);
}
