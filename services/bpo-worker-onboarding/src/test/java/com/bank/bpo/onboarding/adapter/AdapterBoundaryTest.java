package com.bank.bpo.onboarding.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;

import com.bank.bpo.simulator.DomainSimulator;
import com.bank.bpo.workerkit.ProviderException;
import com.bank.bpo.workerkit.ProviderHttp;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DevSecOps B-2, B-3 and S-8 at the adapter boundary: no crafted id reaches a provider, no answer without its
 * required field becomes an output, and each request carries the fields its expectation names.
 */
class AdapterBoundaryTest {

    private static final DomainSimulator SIM = DomainSimulator.start();
    private static final ProviderHttp HTTP = ProviderHttp.loopback(SIM.baseUrl(), () -> "t", Duration.ofSeconds(3));
    private static final List<String> CRAFTED = List.of("..", "x/../y", "%2e%2e", "a.b", "a b", "");

    private final CusKycAdapter cus = new CusKycAdapter(HTTP);
    private final CmpScreeningAdapter screening = new CmpScreeningAdapter(HTTP);
    private final CmpReviewCaseAdapter cases = new CmpReviewCaseAdapter(HTTP);
    private final AccApplicationAdapter acc = new AccApplicationAdapter(HTTP);
    private final PsgTrAliasAdapter psg = new PsgTrAliasAdapter(HTTP);
    private final NtfNotificationAdapter ntf = new NtfNotificationAdapter(HTTP);

    @AfterAll
    static void stop() {
        SIM.close();
    }

    @BeforeEach
    void reset() {
        SIM.reset();
    }

    private static void contractFault(Runnable call) {
        ProviderException e = catchThrowableOfType(ProviderException.class, call::run);
        assertThat(e).isNotNull();
        assertThat(e.kind()).isEqualTo(ProviderException.Kind.CONTRACT);
    }

    @Test
    void aCraftedIdNeverReachesAProvider() {
        List<Consumer<String>> calls = List.of(
            (id) -> cus.statusOf(id),
            (id) -> screening.screen(id, "k"),
            (id) -> cases.open(id, "app-1", "acc-tpp-account-opening", "AE", "k"),
            (id) -> cases.record(id, "CLEAR", "OTHER", "k"),
            (id) -> acc.open(id, "k"),
            (id) -> acc.reject(id, "KYC_REJECTED", "k"),
            (id) -> psg.offer("app-1", id, "k"),
            (id) -> ntf.notify(id, "ACCOUNT_OPENED", "k"));
        for (Consumer<String> call : calls) {
            for (String id : CRAFTED) {
                contractFault(() -> call.accept(id));
            }
            contractFault(() -> call.accept(null));
        }
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void anOpenAnswerWithoutAnAccountIdIsAContractFault() {
        SIM.answerRaw("open-account", 200, "{}", 1);
        contractFault(() -> acc.open("app-1", "app-1:open"));
        SIM.answerRaw("open-account", 201, "{\"accountId\":null}", 1);
        contractFault(() -> acc.open("app-1", "app-1:open"));
        SIM.answerRaw("open-account", 200, "{\"accountId\":\"../x\"}", 1);
        contractFault(() -> acc.open("app-1", "app-1:open"));
    }

    @Test
    void everyOtherRequiredAnswerFieldIsChecked() {
        SIM.answerRaw("screen", 201, "{\"decision\":\"PASS\"}", 1);
        contractFault(() -> screening.screen("app-1", "k"));
        SIM.answerRaw("screen", 201, "{\"screeningId\":\"s1\",\"decision\":\"MAYBE\"}", 1);
        contractFault(() -> screening.screen("app-1", "k"));
        SIM.answerRaw("kyc-status", 200, "{}", 1);
        contractFault(() -> cus.statusOf("app-1"));
        SIM.answerRaw("open-review-case", 201, "{}", 1);
        contractFault(() -> cases.open("s1", "app-1", "acc-tpp-account-opening", "AE", "k"));
        SIM.answerRaw("record-review-decision", 200, "{\"outcome\":\"MAYBE\"}", 1);
        contractFault(() -> cases.record("case-1", "CLEAR", "OTHER", "k"));
        SIM.answerRaw("alias-offer", 200, "{}", 1);
        contractFault(() -> psg.offer("app-1", "acc-1", "k"));
        SIM.answerRaw("notify", 201, "{}", 1);
        contractFault(() -> ntf.notify("app-1", "ACCOUNT_OPENED", "k"));
    }

    @Test
    void aReviewNobodyDecidedIsNeverAClearance() {
        SIM.answerRaw("record-review-decision", 200, "{\"outcome\":\"CLEARED\"}", 1);
        contractFault(() -> cases.record("case-1", "EXPIRED", "SLA_EXPIRED", "k"));
    }

    @Test
    void aReasonThatIsNotItsDecisionsCodeIsRefusedBeforeTheCall() {
        contractFault(() -> cases.record("case-1", "CLEAR", "looks fine", "k"));
        contractFault(() -> cases.record("case-1", "REJECT", null, "k"));
        contractFault(() -> cases.record("case-1", "CLEAR", "SLA_EXPIRED", "k"));
        contractFault(() -> cases.record("case-1", "EXPIRED", "OTHER", "k"));
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void enumInputsOutsideTheirCodesAreRefusedBeforeTheCall() {
        contractFault(() -> acc.reject("app-1", "because", "k"));
        contractFault(() -> acc.reject("app-1", null, "k"));
        contractFault(() -> cases.record("case-1", "ESCALATE", "OTHER", "k"));
        contractFault(() -> cases.record("case-1", null, "OTHER", "k"));
        contractFault(() -> cases.open("s1", "app-1", "acc-tpp-account-opening", "UK", "k"));
        contractFault(() -> ntf.notify("app-1", "PROMOTION", "k"));
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void requestsCarryTheFieldsTheirExpectationNames() {
        cases.open("s1", "app-1", "acc-tpp-account-opening", "TR", "app-1:open-review-case");
        psg.offer("app-1", "acc-1", "app-1:alias-offer");
        ntf.notify("app-1", "ACCOUNT_OPENED", "app-1:notify-account-opened");
        acc.reject("app-2", "KYC_TIMEOUT", "app-2:reject");
        cases.record("case-9", "REJECT", "OTHER", "case-9:record-decision");

        assertThat(SIM.callsTo("/api/v1/compliance/review-cases").get(0).body())
            .containsEntry("sourceProcess", "acc-tpp-account-opening").containsEntry("jurisdiction", "TR");
        assertThat(SIM.callsTo("/api/v1/psg-tr/alias-offers").get(0).body()).containsEntry("applicationId", "app-1").containsEntry("accountId", "acc-1");
        assertThat(SIM.callsTo("/api/v1/notifications").get(0).body()).containsEntry("subjectRef", "app-1").doesNotContainKey("applicationId");
        assertThat(SIM.callsTo("/api/v1/account-applications/app-2/reject").get(0).body()).containsEntry("rejectionReason", "KYC_TIMEOUT");
        assertThat(SIM.callsTo("/api/v1/compliance/review-cases/case-9/decision").get(0).body())
            .containsOnly(entry("decision", "REJECT"), entry("reason", "OTHER"));
    }
}
