package com.bank.bpo.payments.adapter;

import com.bank.bpo.payments.port.RecallPort;
import com.bank.bpo.workerkit.Ids;
import com.bank.bpo.workerkit.ProviderHttp;
import java.util.Map;
import java.util.Set;

/** psg-tr FAST recall requests and their status (contracts/expectations/psg-tr.yaml). */
public final class PsgTrRecallAdapter implements RecallPort {

    private static final Set<String> RAILS = Set.of("FAST");
    private static final Set<String> STATUSES = Set.of("RETURNED", "DECLINED", "PENDING");
    private final ProviderHttp http;

    public PsgTrRecallAdapter(ProviderHttp http) {
        this.http = http;
    }

    private static String base(String rail, String paymentId) {
        Ids.oneOf("rail", rail, RAILS);
        return "/api/v1/psg-tr/fast/instructions/" + Ids.require("paymentId", paymentId);
    }

    @Override
    public String request(String rail, String paymentId, String idempotencyKey) {
        String path = base(rail, paymentId) + "/recall-requests";
        return Ids.require("recallRequestRef", http.post(path, Map.of(), idempotencyKey).get("recallRequestRef"));
    }

    @Override
    public Status statusOf(String rail, String paymentId) {
        // A status this adapter does not know is a contract fault, never a return.
        return Status.valueOf(Ids.oneOf("recallStatus", http.get(base(rail, paymentId) + "/recall-status").get("recallStatus"), STATUSES));
    }
}
