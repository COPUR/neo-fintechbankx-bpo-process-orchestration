package com.bank.bpo.cpt;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.payments.PaymentsWorkerApplication;
import com.bank.bpo.simulator.DomainSimulator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P6 (pay-return-recall, TR FAST profile) on a real Camunda 8.10 engine with the real payments workers talking HTTP
 * to the domain simulator. Every path has a test; the money step (apply-return) is checked for exactly-once effect
 * and is reached only when the gateway's record says RETURNED.
 */
@SpringBootTest(classes = {PaymentsWorkerApplication.class, ModelDeployment.class})
@CamundaSpringProcessTest
class PayReturnRecallTest {

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

    /** What the event bridge does on Payment.RecallRequested: start the instance with the business key and two enums. */
    private ProcessInstanceEvent start(String paymentId) {
        return client.newCreateInstanceCommand()
            .bpmnProcessId("pay-return-recall").latestVersion()
            .variables(Map.of("paymentId", paymentId, "jurisdiction", "TR", "rail", "FAST"))
            .send().join();
    }

    private void answered(String paymentId, Map<String, Object> variables) {
        client.newPublishMessageCommand().messageName("Msg_RecallAnswered").correlationKey(paymentId).variables(variables).send().join();
    }

    private long notifications(String purpose) {
        return SIM.callsTo("/api/v1/notifications").stream().filter((c) -> purpose.equals(c.body().get("purpose"))).count();
    }

    private long reads(String paymentId) {
        return SIM.callsTo("/api/v1/psg-tr/fast/instructions/" + paymentId + "/recall-status").size();
    }

    @Test
    void aReturnTheGatewayReportsIsCreditedOnceAndTheCaseClosesReturned() {
        ProcessInstanceEvent instance = start("rcl-ret-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-ret-1", Map.of());

        assertThat(instance).isCompleted()
            .hasCompletedElementsInOrder("Task_SendRecallRequest", "Msg_RecallAnsweredFirst", "Task_ReadRecallFirst",
                "Task_ApplyReturn", "Task_CloseReturned", "Task_NotifyReturned", "EndEvent_Returned")
            .hasNotActivatedElements("Task_ReadRecallFinal", "Task_CloseUnresolved")
            .hasVariable("journalRef", "jr-return-1").hasVariable("recallRequestRef", "rr-rcl-ret-1");
        assertThat(SIM.recallRequestsSent("rcl-ret-1")).isEqualTo(1);
        assertThat(SIM.callsTo("/api/v1/psg-tr/fast/instructions/rcl-ret-1/recall-requests").get(0).idempotencyKey())
            .isEqualTo("rcl-ret-1:recall-request");
        assertThat(SIM.callsTo("/api/v1/payments/rcl-ret-1/recall/apply-return").get(0).idempotencyKey()).isEqualTo("rcl-ret-1:apply-return");
        assertThat(SIM.returnsApplied()).isEqualTo(1);
        assertThat(SIM.recallOutcomeRecordedFor("rcl-ret-1")).isEqualTo("RETURNED");
        assertThat(notifications("RECALL_RETURNED")).isEqualTo(1);
    }

    @Test
    void aDeclinedRecallClosesUnresolvedAndCreditsNothing() {
        SIM.recallStatuses("rcl-dec-1", "DECLINED");

        ProcessInstanceEvent instance = start("rcl-dec-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-dec-1", Map.of());

        assertThat(instance).isCompleted()
            .hasCompletedElements("Task_ReadRecallFirst", "Task_CloseUnresolved", "Task_NotifyUnresolved", "EndEvent_Unresolved")
            .hasNotActivatedElements("Task_ApplyReturn", "Gateway_AwaitFinal");
        assertThat(SIM.returnsApplied()).isZero();
        assertThat(SIM.recallOutcomeRecordedFor("rcl-dec-1")).isEqualTo("UNRESOLVED");
        assertThat(notifications("RECALL_UNRESOLVED")).isEqualTo(1);
    }

    @Test
    void anAnswerEventThatClaimsReturnedWhileTheGatewaySaysDeclinedCreditsNothing() {
        // The message only wakes the instance: the route follows psg's record, not the payload.
        SIM.recallStatuses("rcl-forged-1", "DECLINED");

        ProcessInstanceEvent instance = start("rcl-forged-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-forged-1", Map.of("recallStatus", "RETURNED", "recallOutcome", "RETURNED", "paymentId", "rcl-other-1"));

        assertThat(instance).isCompleted().hasCompletedElements("EndEvent_Unresolved").hasNotActivatedElements("Task_ApplyReturn")
            .hasVariable("recallStatus", "DECLINED").hasVariable("paymentId", "rcl-forged-1");
        assertThat(SIM.returnsApplied()).isZero();
    }

    @Test
    void noAnswerWithinTheFirstWaitReadsTheGatewayAndALateReturnIsStillCredited() {
        SIM.recallStatuses("rcl-late-1", "PENDING", "RETURNED");

        ProcessInstanceEvent instance = start("rcl-late-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        context.increaseTime(Duration.ofDays(2).plusSeconds(1));
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFinal");
        answered("rcl-late-1", Map.of());

        assertThat(instance).isCompleted()
            .hasCompletedElements("Timer_FirstWait", "Msg_RecallAnsweredFinal", "Task_ReadRecallFinal", "Task_ApplyReturn", "EndEvent_Returned");
        assertThat(reads("rcl-late-1")).isEqualTo(2);
        assertThat(SIM.returnsApplied()).isEqualTo(1);
    }

    @Test
    void stillPendingAfterTheFinalWaitClosesUnresolvedAndTheWaitsAreTheTurkishOnes() {
        SIM.recallStatuses("rcl-none-1", "PENDING");

        ProcessInstanceEvent instance = start("rcl-none-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        context.increaseTime(Duration.ofDays(1));
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        context.increaseTime(Duration.ofDays(1).plusSeconds(1));
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFinal");
        context.increaseTime(Duration.ofDays(5).plusSeconds(1));

        assertThat(instance).isCompleted()
            .hasCompletedElements("Timer_FirstWait", "Timer_FinalWait", "Task_ReadRecallFinal", "EndEvent_Unresolved")
            .hasNotActivatedElements("Task_ApplyReturn");
        assertThat(reads("rcl-none-1")).isEqualTo(2);
        assertThat(SIM.recallRequestsSent("rcl-none-1")).isEqualTo(1);
        assertThat(notifications("RECALL_UNRESOLVED")).isEqualTo(1);
    }

    @Test
    void aReturnPayCannotMatchClosesUnresolvedWithoutACredit() {
        SIM.failWith("apply-return", 422, "RETURN_NOT_MATCHED", 1);

        ProcessInstanceEvent instance = start("rcl-nomatch-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-nomatch-1", Map.of());

        assertThat(instance).isCompleted()
            .hasCompletedElements("BoundaryEvent_ReturnNotMatched", "Task_CloseUnresolved", "EndEvent_Unresolved")
            .hasNotActivatedElements("Task_CloseReturned", "Task_NotifyReturned");
        assertThat(SIM.returnsApplied()).isZero();
        assertThat(SIM.recallOutcomeRecordedFor("rcl-nomatch-1")).isEqualTo("UNRESOLVED");
        assertThat(notifications("RECALL_RETURNED")).isZero();
    }

    @Test
    void aLostResponseOnTheCreditRetriesWithTheSameKeyAndCreditsOnce() {
        SIM.loseResponse("apply-return", 1);

        ProcessInstanceEvent instance = start("rcl-lost-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-lost-1", Map.of());

        assertThat(instance).isCompleted().hasCompletedElements("Task_ApplyReturn", "EndEvent_Returned");
        assertThat(SIM.callsTo("/api/v1/payments/rcl-lost-1/recall/apply-return")).hasSize(2)
            .allSatisfy((c) -> assertThat(c.idempotencyKey()).isEqualTo("rcl-lost-1:apply-return"));
        assertThat(SIM.returnsApplied()).isEqualTo(1);
    }

    @Test
    void aRecallStatusTheContractDoesNotKnowStopsOnAnIncident() {
        SIM.answerRaw("recall-status", 200, "{\"recallStatus\":\"PARTIAL\"}", 1);

        ProcessInstanceEvent instance = start("rcl-unknown-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-unknown-1", Map.of());

        assertThat(instance).isActive().hasActiveIncidents().hasActiveElements("Task_ReadRecallFirst")
            .hasNotActivatedElements("Task_ApplyReturn", "Task_CloseUnresolved");
    }

    @Test
    void aRailWithoutAGatewayIsAnIncidentAndNoRequestLeaves() {
        ProcessInstanceEvent instance = client.newCreateInstanceCommand()
            .bpmnProcessId("pay-return-recall").latestVersion()
            .variables(Map.of("paymentId", "rcl-aani-1", "jurisdiction", "AE", "rail", "AANI"))
            .send().join();

        assertThat(instance).isActive().hasActiveIncidents().hasActiveElements("Task_SendRecallRequest");
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void theInstanceCarriesOnlyIdsAndEnumsUnderTheSizeLimit() {
        ProcessInstanceEvent instance = start("rcl-vars-1");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitFirst");
        answered("rcl-vars-1", Map.of());

        assertThat(instance).isCompleted();
        var variables = client.newVariableSearchRequest().filter((f) -> f.processInstanceKey(instance.getProcessInstanceKey()))
            .send().join().items().stream()
            .filter((v) -> v.getScopeKey() == instance.getProcessInstanceKey()).toList();
        assertThat(variables).extracting((v) -> v.getName())
            .containsOnly("paymentId", "jurisdiction", "rail", "recallPolicy", "recallRequestRef", "recallSignal", "recallStatus",
                "journalRef", "notificationRef");
        int bytes = variables.stream().mapToInt((v) -> v.getValue().length()).sum();
        assertThat(bytes).isLessThan(8 * 1024);
    }
}
