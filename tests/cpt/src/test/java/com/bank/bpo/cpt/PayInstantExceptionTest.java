package com.bank.bpo.cpt;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.payments.PaymentsWorkerApplication;
import com.bank.bpo.simulator.DomainSimulator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P2 (pay-instant-exception, TR FAST profile) and its library process lib-await-external-status, run on a real
 * Camunda 8.10 engine with the real payments workers talking HTTP to the domain simulator. Every path of the model
 * has a test; the money-moving step (capture-hold) is checked for exactly-once effect, and no path sends anything
 * to the scheme.
 */
@SpringBootTest(classes = {PaymentsWorkerApplication.class, ModelDeployment.class})
@CamundaSpringProcessTest
class PayInstantExceptionTest {

    private static final DomainSimulator SIM = DomainSimulator.start();

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        for (String provider : new String[] {"psg-tr", "acc", "pay", "ntf"}) {
            registry.add("bpo.providers." + provider + ".base-url", () -> SIM.baseUrl().toString());
        }
    }

    @AfterAll
    static void stopSimulator() {
        SIM.close();
    }

    @Autowired
    private CamundaClient client;

    @Autowired
    private CamundaProcessTestContext context;

    @BeforeEach
    void resetSimulator() {
        SIM.reset();
    }

    /** What the event bridge does on Payment.ExceptionRaised: start the instance with the business key and two enums. */
    private ProcessInstanceEvent start(String paymentId) {
        return client.newCreateInstanceCommand()
            .bpmnProcessId("pay-instant-exception").latestVersion()
            .variables(Map.of("paymentId", paymentId, "jurisdiction", "TR", "rail", "FAST"))
            .send().join();
    }

    /** The status inquiry runs in a child instance of lib-await-external-status; wait until it waits at the given element. */
    private void awaitLibrary(String elementId) {
        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isActive().hasActiveElements(elementId);
    }

    /** Every inquiry stays PDNG: the three growing waits pass and the library gives up with Err_StatusTimeout. */
    private void letEveryWaitPass() {
        awaitLibrary("Gateway_AwaitFirst");
        context.increaseTime(Duration.ofSeconds(31));
        awaitLibrary("Gateway_AwaitSecond");
        context.increaseTime(Duration.ofMinutes(2).plusSeconds(1));
        awaitLibrary("Gateway_AwaitThird");
        context.increaseTime(Duration.ofMinutes(10).plusSeconds(1));
    }

    private long notifications(String purpose) {
        return SIM.callsTo("/api/v1/notifications").stream().filter((c) -> purpose.equals(c.body().get("purpose"))).count();
    }

    private long inquiries(String paymentId) {
        return SIM.callsTo("/api/v1/psg-tr/fast/instructions/" + paymentId + "/status").size();
    }

    @Test
    void aSettledInstructionCapturesTheHoldOnceAndTellsPayAndTheCustomer() {
        ProcessInstanceEvent instance = start("pay-acsc-1");

        assertThat(instance).isCompleted()
            .hasCompletedElementsInOrder("Task_AwaitSchemeStatus", "Task_CaptureHold", "Task_MarkSettled", "Task_NotifySettled", "EndEvent_Settled")
            .hasNotActivatedElements("Task_ReleaseHold", "Task_OpsReview")
            .hasVariable("journalRef", "jr-1");
        assertThat(SIM.holdsCaptured()).isEqualTo(1);
        assertThat(SIM.callsTo("/api/v1/holds/pay-acsc-1/capture").get(0).idempotencyKey()).isEqualTo("pay-acsc-1:capture");
        assertThat(SIM.callsTo("/api/v1/payments/pay-acsc-1/exception/settle").get(0).body()).containsEntry("journalRef", "jr-1");
        assertThat(notifications("PAYMENT_SETTLED")).isEqualTo(1);
        assertThat(inquiries("pay-acsc-1")).isEqualTo(1);
    }

    @Test
    void aRejectedInstructionReleasesTheHoldAndCapturesNothing() {
        SIM.schemeStatuses("pay-rjct-1", "RJCT");

        ProcessInstanceEvent instance = start("pay-rjct-1");

        assertThat(instance).isCompleted()
            .hasCompletedElementsInOrder("Task_ReleaseHold", "Task_MarkRejected", "Task_NotifyRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_CaptureHold");
        assertThat(SIM.holdsReleased("pay-rjct-1")).isEqualTo(1);
        assertThat(SIM.callsTo("/api/v1/holds/pay-rjct-1/release").get(0).idempotencyKey()).isEqualTo("pay-rjct-1:release");
        assertThat(SIM.holdsCaptured()).isZero();
        assertThat(notifications("PAYMENT_REJECTED")).isEqualTo(1);
    }

    @Test
    void aPendingInstructionWaitsAndTheGatewayEventWakesItForAFreshRead() {
        SIM.schemeStatuses("pay-pdng-1", "PDNG", "ACSC");

        ProcessInstanceEvent instance = start("pay-pdng-1");
        awaitLibrary("Gateway_AwaitFirst");
        client.newPublishMessageCommand().messageName("Msg_SchemeStatusReported").correlationKey("pay-pdng-1").send().join();

        assertThat(instance).isCompleted().hasCompletedElements("Task_CaptureHold", "EndEvent_Settled");
        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isCompleted()
            .hasCompletedElements("Msg_StatusReportedFirst", "Task_InquireSecond", "EndEvent_StatusKnown");
        assertThat(inquiries("pay-pdng-1")).isEqualTo(2);
        assertThat(SIM.holdsCaptured()).isEqualTo(1);
    }

    @Test
    void anEventThatClaimsSettledWhilePsgSaysRejectedCapturesNothing() {
        // The message only wakes the instance (DevSecOps B-4): the route follows psg's record, not the payload.
        SIM.schemeStatuses("pay-forged-1", "PDNG", "RJCT");

        ProcessInstanceEvent instance = start("pay-forged-1");
        awaitLibrary("Gateway_AwaitFirst");
        client.newPublishMessageCommand().messageName("Msg_SchemeStatusReported").correlationKey("pay-forged-1")
            .variables(Map.of("schemeStatus", "ACSC", "exceptionOutcome", "SETTLE")).send().join();

        assertThat(instance).isCompleted().hasCompletedElements("Task_ReleaseHold", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_CaptureHold").hasVariable("schemeStatus", "RJCT");
        assertThat(SIM.holdsCaptured()).isZero();
    }

    @Test
    void theTimersGrowAndAStatusFoundOnALaterReadStillSettles() {
        SIM.schemeStatuses("pay-late-1", "PDNG", "PDNG", "ACSC");

        ProcessInstanceEvent instance = start("pay-late-1");
        awaitLibrary("Gateway_AwaitFirst");
        context.increaseTime(Duration.ofSeconds(31));
        awaitLibrary("Gateway_AwaitSecond");
        context.increaseTime(Duration.ofMinutes(2).plusSeconds(1));

        assertThat(instance).isCompleted().hasCompletedElements("Task_CaptureHold", "EndEvent_Settled");
        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isCompleted()
            .hasCompletedElements("Timer_FirstWait", "Timer_SecondWait", "Task_InquireThird").hasNotActivatedElements("Task_InquireLast");
        assertThat(inquiries("pay-late-1")).isEqualTo(3);
    }

    @Test
    void aStatusStillUnknownAfterTheLastReadGoesToTheTurkishFastQueueAndSettlesOnItsDecision() {
        SIM.schemeStatuses("pay-ops-1", "PDNG");

        ProcessInstanceEvent instance = start("pay-ops-1");
        letEveryWaitPass();

        assertThat(instance).isActive().hasActiveElements("Task_OpsReview");
        assertThat(UserTaskSelectors.byElementId("Task_OpsReview")).hasCandidateGroups(List.of("pay-tr-fast-ops"));
        assertThat(inquiries("pay-ops-1")).isEqualTo(4);
        assertThat(SIM.holdsCaptured()).isZero();
        context.completeUserTask(UserTaskSelectors.byElementId("Task_OpsReview"), Map.of("exceptionDecision", "SETTLED"));

        assertThat(instance).isCompleted()
            .hasCompletedElements("BoundaryEvent_StatusTimeout", "Task_RecordExceptionDecision", "Task_CaptureHold", "EndEvent_Settled")
            .hasVariable("exceptionOutcome", "SETTLE");
        assertThat(SIM.exceptionDecisionRecordedFor("pay-ops-1")).isEqualTo("SETTLED");
        assertThat(SIM.holdsCaptured()).isEqualTo(1);
    }

    @Test
    void anOperationsRejectionReleasesTheHold() {
        SIM.schemeStatuses("pay-ops-2", "PDNG");

        ProcessInstanceEvent instance = start("pay-ops-2");
        letEveryWaitPass();
        assertThat(instance).isActive().hasActiveElements("Task_OpsReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_OpsReview"), Map.of("exceptionDecision", "REJECTED"));

        assertThat(instance).isCompleted().hasCompletedElements("Task_ReleaseHold", "Task_MarkRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_CaptureHold");
        assertThat(SIM.holdsReleased("pay-ops-2")).isEqualTo(1);
    }

    @Test
    void aCompletionThatAlsoSendsABusinessKeyChangesOnlyTheDecision() {
        SIM.schemeStatuses("pay-ops-5", "PDNG");

        ProcessInstanceEvent instance = start("pay-ops-5");
        letEveryWaitPass();
        assertThat(instance).isActive().hasActiveElements("Task_OpsReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_OpsReview"),
            Map.of("exceptionDecision", "SETTLED", "paymentId", "pay-other-1", "jurisdiction", "AE", "rail", "AANI"));

        assertThat(instance).isCompleted().hasCompletedElements("Task_CaptureHold", "EndEvent_Settled")
            .hasVariable("paymentId", "pay-ops-5").hasVariable("jurisdiction", "TR").hasVariable("rail", "FAST");
        assertThat(SIM.callsTo("/api/v1/holds/pay-ops-5/capture")).hasSize(1);
        assertThat(SIM.callsTo("/api/v1/holds/pay-other-1/capture")).isEmpty();
        assertThat(SIM.exceptionDecisionRecordedFor("pay-other-1")).isNull();
    }

    @Test
    void anEscalationGoesToTheSupervisorQueue() {
        SIM.schemeStatuses("pay-ops-3", "PDNG");

        ProcessInstanceEvent instance = start("pay-ops-3");
        letEveryWaitPass();
        assertThat(instance).isActive().hasActiveElements("Task_OpsReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_OpsReview"), Map.of("exceptionDecision", "ESCALATE"));
        assertThat(instance).isActive().hasActiveElements("Task_SupervisorReview");
        assertThat(UserTaskSelectors.byElementId("Task_SupervisorReview")).hasCandidateGroups(List.of("pay-tr-fast-supervisor"));
        context.completeUserTask(UserTaskSelectors.byElementId("Task_SupervisorReview"), Map.of("exceptionDecision", "SETTLED"));

        assertThat(instance).isCompleted().hasCompletedElements("Task_SupervisorReview", "Task_CaptureHold", "EndEvent_Settled");
        assertThat(SIM.exceptionDecisionRecordedFor("pay-ops-3")).isEqualTo("SETTLED");
    }

    @Test
    void whenNobodyDecidesPayTakesThePaymentBackAndNoMoneyMoves() {
        SIM.schemeStatuses("pay-ops-4", "PDNG");

        ProcessInstanceEvent instance = start("pay-ops-4");
        letEveryWaitPass();
        assertThat(instance).isActive().hasActiveElements("Task_OpsReview");
        context.increaseTime(Duration.ofMinutes(30).plusSeconds(1));
        assertThat(instance).isActive().hasActiveElements("Task_SupervisorReview");
        context.increaseTime(Duration.ofHours(2).plusSeconds(1));

        assertThat(instance).isCompleted()
            .hasCompletedElements("BoundaryEvent_OpsSla", "BoundaryEvent_SupervisorSla", "Event_DecisionExpired", "EndEvent_HandedBack")
            .hasNotActivatedElements("Task_CaptureHold", "Task_ReleaseHold").hasVariable("exceptionOutcome", "HANDED_BACK");
        assertThat(SIM.exceptionDecisionRecordedFor("pay-ops-4")).isEqualTo("EXPIRED");
        assertThat(SIM.holdsCaptured()).isZero();
        assertThat(SIM.holdsReleased("pay-ops-4")).isZero();
        assertThat(notifications("PAYMENT_SETTLED") + notifications("PAYMENT_REJECTED")).isZero();
    }

    @Test
    void aLostResponseOnCaptureRetriesWithTheSameKeyAndCapturesOnce() {
        SIM.loseResponse("capture-hold", 1);

        ProcessInstanceEvent instance = start("pay-lost-1");

        assertThat(instance).isCompleted().hasCompletedElements("Task_CaptureHold", "EndEvent_Settled");
        assertThat(SIM.callsTo("/api/v1/holds/pay-lost-1/capture")).hasSize(2)
            .allSatisfy((c) -> assertThat(c.idempotencyKey()).isEqualTo("pay-lost-1:capture"));
        assertThat(SIM.holdsCaptured()).isEqualTo(1);
    }

    @Test
    void aHoldThatAlreadyLapsedStopsOnAnIncidentAndTellsNobody() {
        // 05 §6.4: acc let the hold lapse, so the capture cannot happen; an operator decides, the model does not guess.
        SIM.failWith("capture-hold", 410, "HOLD_EXPIRED", 1);

        ProcessInstanceEvent instance = start("pay-lapsed-1");

        assertThat(instance).isActive().hasActiveIncidents().hasActiveElements("Task_CaptureHold")
            .hasNotActivatedElements("Task_MarkSettled", "Task_NotifySettled");
        assertThat(notifications("PAYMENT_SETTLED")).isZero();
    }

    @Test
    void aSchemeStatusTheContractDoesNotKnowStopsOnAnIncident() {
        SIM.answerRaw("scheme-status", 200, "{\"schemeStatus\":\"ACCP\"}", 1);

        ProcessInstanceEvent instance = start("pay-odd-1");

        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isActive().hasActiveIncidents();
        assertThat(instance).isActive().hasNotActivatedElements("Task_CaptureHold", "Task_ReleaseHold");
        assertThat(SIM.holdsCaptured()).isZero();
    }

    @Test
    void aPaymentIdThatCouldClimbAPathNeverReachesAProvider() {
        ProcessInstanceEvent instance = start("../token");

        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isActive().hasActiveIncidents();
        assertThat(instance).isActive().hasNotActivatedElements("Task_CaptureHold");
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void aRailWithoutAGatewayIsAnIncidentNotAGuess() {
        ProcessInstanceEvent instance = client.newCreateInstanceCommand().bpmnProcessId("pay-instant-exception").latestVersion()
            .variables(Map.of("paymentId", "pay-aani-1", "jurisdiction", "AE", "rail", "AANI")).send().join();

        assertThat(ProcessInstanceSelectors.byProcessId("lib-await-external-status")).isActive().hasActiveIncidents();
        assertThat(instance).isActive().hasNotActivatedElements("Task_CaptureHold", "Task_ReleaseHold");
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void theRoutingAndPolicyTablesHaveDefaultsForOtherCallers() {
        var routing = client.newEvaluateDecisionCommand().decisionId("pay-exception-routing")
            .variables(Map.of("jurisdiction", "AE", "rail", "AANI")).send().join();
        assertThat(routing.getDecisionOutput()).contains("\"opsQueue\":\"pay-ops-l1\"", "\"supervisorSla\":\"PT4H\"");
        var fast = client.newEvaluateDecisionCommand().decisionId("psg-status-inquiry-policy").variables(Map.of("rail", "FAST")).send().join();
        assertThat(fast.getDecisionOutput()).contains("\"firstWait\":\"PT30S\"", "\"secondWait\":\"PT2M\"", "\"thirdWait\":\"PT10M\"");
    }

    @Test
    void theInstanceCarriesOnlyIdsAndEnumsUnderTheSizeLimit() {
        ProcessInstanceEvent instance = start("pay-vars-1");

        assertThat(instance).isCompleted();
        var variables = client.newVariableSearchRequest().filter((f) -> f.processInstanceKey(instance.getProcessInstanceKey()))
            .send().join().items().stream()
            .filter((v) -> v.getScopeKey() == instance.getProcessInstanceKey()).toList();
        assertThat(variables).extracting((v) -> v.getName())
            .containsOnly("paymentId", "jurisdiction", "rail", "schemeStatus", "journalRef", "notificationRef");
        int bytes = variables.stream().mapToInt((v) -> v.getValue().length()).sum();
        assertThat(bytes).isLessThan(8 * 1024);
    }
}
