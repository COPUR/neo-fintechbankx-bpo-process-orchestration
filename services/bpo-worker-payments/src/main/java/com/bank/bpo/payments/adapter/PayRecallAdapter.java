package com.bank.bpo.payments.adapter;

import com.bank.bpo.payments.port.PaymentRecallPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;

/** pay recall cases: apply the reported return, close the case (contracts/expectations/pay.yaml). */
public final class PayRecallAdapter implements PaymentRecallPort {

    private final ProviderHttp http;

    public PayRecallAdapter(ProviderHttp http) {
        this.http = http;
    }

    private static String base(String paymentId) {
        return "/api/v1/payments/" + Ids.require("paymentId", paymentId) + "/recall";
    }

    @Override
    public String applyReturn(String paymentId, String idempotencyKey) {
        // No journalRef in a 2xx is a contract fault: the instance stops instead of telling the customer the money is back.
        return Ids.require("journalRef", http.post(base(paymentId) + "/apply-return", Map.of(), idempotencyKey).get("journalRef"));
    }

    @Override
    public void close(String paymentId, Outcome outcome, String idempotencyKey) {
        http.post(base(paymentId) + "/close", Map.of("outcome", outcome.name()), idempotencyKey);
    }
}
