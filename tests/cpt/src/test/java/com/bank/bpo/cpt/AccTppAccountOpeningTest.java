package com.bank.bpo.cpt;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.onboarding.OnboardingWorkerApplication;
import com.bank.bpo.simulator.DomainSimulator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
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
 * P1 (acc-tpp-account-opening) and its library process lib-screening-review, run on a real Camunda 8.10
 * engine with the real onboarding workers talking HTTP to the domain simulator. Every path of the model
 * has a test; the money-moving step (open-account) is checked for exactly-once effect.
 */
@SpringBootTest(classes = {OnboardingWorkerApplication.class, ModelDeployment.class})
@CamundaSpringProcessTest
class AccTppAccountOpeningTest {

    private static final DomainSimulator SIM = DomainSimulator.start();

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        for (String provider : new String[] {"cus", "cmp", "acc", "psg-tr", "ntf"}) {
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

    private ProcessInstanceEvent start(String applicationId, String jurisdiction) {
        return client.newCreateInstanceCommand()
            .bpmnProcessId("acc-tpp-account-opening").latestVersion()
            .variables(Map.of("applicationId", applicationId, "jurisdiction", jurisdiction))
            .send().join();
    }

    /** cus finishes KYC with {@code status}, then the event wakes the instance; the message carries nothing. */
    private void kycCompleted(String applicationId, String status) {
        SIM.kyc(applicationId, status);
        client.newPublishMessageCommand().messageName("Msg_KycCompleted").correlationKey(applicationId).send().join();
    }

    /** The review runs in a child instance of lib-screening-review; wait until it waits at the given element. */
    private void awaitReview(String elementId) {
        assertThat(ProcessInstanceSelectors.byProcessId("lib-screening-review")).isActive().hasActiveElements(elementId);
    }

    private long notifications(String purpose) {
        return SIM.callsTo("/api/v1/notifications").stream().filter((c) -> purpose.equals(c.body().get("purpose"))).count();
    }

    @Test
    void verifiedAndClearedOpensTheAccountInAe() {
        ProcessInstanceEvent instance = start("app-ae-1", "AE");

        assertThat(instance).isCompleted()
            .hasCompletedElementsInOrder("Task_VerifyKyc", "Task_Screen", "Task_OpenAccount", "Task_NotifyOpened", "EndEvent_AccountOpened")
            .hasNotActivatedElements("Task_OfferKolayAdres", "Task_RejectApplication");
        assertThat(SIM.accountsOpened()).isEqualTo(1);
        assertThat(SIM.callsTo("/api/v1/account-applications/app-ae-1/open").get(0).idempotencyKey()).isEqualTo("app-ae-1:open");
        assertThat(notifications("ACCOUNT_OPENED")).isEqualTo(1);
    }

    @Test
    void trApplicantAlsoGetsTheKolayAdresOffer() {
        ProcessInstanceEvent instance = start("app-tr-1", "TR");

        assertThat(instance).isCompleted().hasCompletedElements("Task_OpenAccount", "Task_OfferKolayAdres", "EndEvent_AccountOpened");
        assertThat(SIM.callsTo("/api/v1/psg-tr/alias-offers")).hasSize(1);
        assertThat(SIM.callsTo("/api/v1/psg-tr/alias-offers").get(0).body()).containsEntry("accountId", "acc-1");
    }

    @Test
    void rejectedKycRejectsTheApplicationWithoutScreening() {
        SIM.kyc("app-kyc-1", "REJECTED");

        ProcessInstanceEvent instance = start("app-kyc-1", "AE");

        assertThat(instance).isCompleted().hasCompletedElements("Event_KycRejected", "Task_RejectApplication", "Task_NotifyRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_Screen", "Task_OpenAccount");
        assertThat(SIM.callsTo("/api/v1/account-applications/app-kyc-1/reject").get(0).body()).containsEntry("rejectionReason", "KYC_REJECTED");
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void pendingKycWaitsForTheMessageThenContinues() {
        SIM.kyc("app-pend-1", "PENDING");

        ProcessInstanceEvent instance = start("app-pend-1", "AE");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitKyc");

        kycCompleted("app-pend-1", "VERIFIED");

        assertThat(instance).isCompleted().hasCompletedElements("Msg_KycCompleted", "Task_Screen", "Task_OpenAccount", "EndEvent_AccountOpened");
        assertThat(SIM.accountsOpened()).isEqualTo(1);
    }

    @Test
    void pendingKycThatTurnsOutRejectedRejectsTheApplication() {
        SIM.kyc("app-pend-2", "PENDING");

        ProcessInstanceEvent instance = start("app-pend-2", "AE");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitKyc");
        kycCompleted("app-pend-2", "REJECTED");

        assertThat(instance).isCompleted().hasCompletedElements("Gateway_KycOutcome", "Event_KycRejected", "EndEvent_Rejected");
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void aKycMessageThatClaimsVerifiedWhileCusSaysPendingOpensNoAccount() {
        // DevSecOps B-4: whatever the message says, the gate routes on what cus answers.
        SIM.kyc("app-forged-1", "PENDING");

        ProcessInstanceEvent instance = start("app-forged-1", "AE");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitKyc");
        client.newPublishMessageCommand().messageName("Msg_KycCompleted").correlationKey("app-forged-1")
            .variables(Map.of("kycStatus", "VERIFIED", "screeningDecision", "CLEAR")).send().join();

        assertThat(instance).isCompleted().hasCompletedElements("Msg_KycCompleted", "Task_RecheckKyc", "Event_KycRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_Screen", "Task_OpenAccount")
            .hasVariable("kycStatus", "PENDING").hasVariable("kycSignal", "RECEIVED");
        assertThat(SIM.callsTo("/api/v1/applications/app-forged-1/kyc")).hasSize(2);
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void documentsNotReceivedWithinSevenDaysRejectTheApplication() {
        SIM.kyc("app-late-1", "PENDING");

        ProcessInstanceEvent instance = start("app-late-1", "AE");
        assertThat(instance).isActive().hasActiveElements("Gateway_AwaitKyc");

        context.increaseTime(Duration.ofDays(7).plusMinutes(1));

        assertThat(instance).isCompleted().hasCompletedElements("Timer_KycDocsTimeout", "Event_KycTimedOut", "EndEvent_Rejected");
        assertThat(SIM.callsTo("/api/v1/account-applications/app-late-1/reject").get(0).body()).containsEntry("rejectionReason", "KYC_TIMEOUT");
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void rejectedScreeningRejectsTheApplication() {
        SIM.screening("app-scr-1", "FAIL");

        ProcessInstanceEvent instance = start("app-scr-1", "AE");

        assertThat(instance).isCompleted().hasCompletedElements("Event_ScreeningRejected", "EndEvent_Rejected").hasNotActivatedElements("Task_OpenAccount");
        assertThat(SIM.callsTo("/api/v1/account-applications/app-scr-1/reject").get(0).body()).containsEntry("rejectionReason", "SCREENING_REJECTED");
    }

    @Test
    void reviewClearedByTheAnalystOpensTheAccount() {
        SIM.screening("app-rev-1", "REVIEW");

        ProcessInstanceEvent instance = start("app-rev-1", "AE");
        assertThat(instance).isActive().hasActiveElements("Task_ScreeningReview");
        awaitReview("Task_AnalystReview");
        assertThat(UserTaskSelectors.byElementId("Task_AnalystReview")).hasCandidateGroups(java.util.List.of("cmp-ae-onboarding-l1"));
        context.completeUserTask(UserTaskSelectors.byElementId("Task_AnalystReview"), Map.of("reviewDecision", "CLEAR"));

        assertThat(instance).isCompleted().hasCompletedElements("Task_ScreeningReview", "Task_OpenAccount", "EndEvent_AccountOpened");
        assertThat(SIM.decisionRecordedFor("app-rev-1")).isEqualTo("CLEAR");
    }

    @Test
    void trReviewGoesToTheTurkishOnboardingQueue() {
        SIM.screening("app-rev-tr", "REVIEW");

        ProcessInstanceEvent instance = start("app-rev-tr", "TR");
        awaitReview("Task_AnalystReview");
        assertThat(UserTaskSelectors.byElementId("Task_AnalystReview")).hasCandidateGroups(java.util.List.of("cmp-tr-onboarding-l1"));
        context.completeUserTask(UserTaskSelectors.byElementId("Task_AnalystReview"), Map.of("reviewDecision", "CLEAR"));

        assertThat(instance).isCompleted().hasCompletedElements("Task_OpenAccount", "Task_OfferKolayAdres", "EndEvent_AccountOpened");
    }

    @Test
    void aCallerWithoutItsOwnRoutingRuleGetsTheDefaultQueues() {
        var result = client.newEvaluateDecisionCommand().decisionId("cmp-review-routing")
            .variables(Map.of("jurisdiction", "AE", "sourceProcess", "pay-instant-exception")).send().join();

        assertThat(result.getDecisionOutput()).contains("\"reviewQueue\":\"cmp-review-l1\"", "\"escalationSla\":\"PT24H\"");
    }

    @Test
    void reviewRejectedByTheAnalystRejectsThroughTheBpmnError() {
        SIM.screening("app-rev-2", "REVIEW");

        ProcessInstanceEvent instance = start("app-rev-2", "AE");
        awaitReview("Task_AnalystReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_AnalystReview"), Map.of("reviewDecision", "REJECT"));

        assertThat(instance).isCompleted().hasCompletedElements("BoundaryEvent_ScreeningRejected", "Event_ScreeningRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_OpenAccount");
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void reviewEscalatedToTheSeniorReviewerThenCleared() {
        SIM.screening("app-rev-3", "REVIEW");

        ProcessInstanceEvent instance = start("app-rev-3", "AE");
        awaitReview("Task_AnalystReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_AnalystReview"), Map.of("reviewDecision", "ESCALATE"));
        awaitReview("Task_SeniorReview");
        context.completeUserTask(UserTaskSelectors.byElementId("Task_SeniorReview"), Map.of("reviewDecision", "CLEAR"));

        assertThat(ProcessInstanceSelectors.byProcessId("lib-screening-review")).isCompleted().hasCompletedElements("Task_SeniorReview", "EndEvent_Cleared");
        assertThat(instance).isCompleted().hasCompletedElements("Task_OpenAccount", "EndEvent_AccountOpened");
        assertThat(SIM.decisionRecordedFor("app-rev-3")).isEqualTo("CLEAR");
    }

    @Test
    void analystSlaExpiryEscalatesAndSeniorSlaExpiryFailsClosed() {
        SIM.screening("app-rev-4", "REVIEW");

        ProcessInstanceEvent instance = start("app-rev-4", "AE");
        assertThat(instance).isActive().hasActiveElements("Task_ScreeningReview");
        awaitReview("Task_AnalystReview");
        context.increaseTime(Duration.ofHours(4).plusMinutes(1));
        awaitReview("Task_SeniorReview");
        context.increaseTime(Duration.ofHours(8).plusMinutes(1));

        // The library ends with the error end event Err_ScreeningRejected, which terminates the child.
        assertThat(ProcessInstanceSelectors.byProcessId("lib-screening-review")).isTerminated()
            .hasCompletedElements("BoundaryEvent_AnalystSla", "BoundaryEvent_SeniorSla", "Event_ReviewExpired");
        assertThat(instance).isCompleted().hasCompletedElements("Event_ScreeningRejected", "EndEvent_Rejected")
            .hasNotActivatedElements("Task_OpenAccount");
        assertThat(SIM.decisionRecordedFor("app-rev-4")).isEqualTo("EXPIRED");
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void aLostResponseOnOpenAccountRetriesWithTheSameKeyAndOpensOneAccount() {
        SIM.loseResponse("open-account", 1);

        ProcessInstanceEvent instance = start("app-lost-1", "AE");

        assertThat(instance).isCompleted().hasCompletedElements("Task_OpenAccount", "EndEvent_AccountOpened");
        assertThat(SIM.callsTo("/api/v1/account-applications/app-lost-1/open")).hasSize(2)
            .allSatisfy((c) -> assertThat(c.idempotencyKey()).isEqualTo("app-lost-1:open"));
        assertThat(SIM.accountsOpened()).isEqualTo(1);
    }

    @Test
    void anOpenAnswerWithoutAnAccountIdStopsOnAnIncidentAndTellsNobody() {
        // DevSecOps B-2: a proxy page or an empty object on the pivot is not an opened account.
        SIM.answerRaw("open-account", 200, "{}", 1);

        ProcessInstanceEvent instance = start("app-noid-1", "AE");

        assertThat(instance).isActive().hasActiveIncidents().hasActiveElements("Task_OpenAccount").hasNotActivatedElements("Task_NotifyOpened");
        assertThat(notifications("ACCOUNT_OPENED")).isZero();
    }

    @Test
    void anApplicationIdThatCouldClimbAPathNeverReachesAProvider() {
        // DevSecOps B-3: the id is refused before any call, with the worker's token never sent.
        ProcessInstanceEvent instance = start("../token", "AE");

        assertThat(instance).isActive().hasActiveIncidents().hasNotActivatedElements("Task_Screen");
        assertThat(SIM.calls()).isEmpty();
    }

    @Test
    void aProviderThatKeepsFailingRaisesAnIncidentAfterTheRetriesAreUsed() {
        SIM.failBeforeEffect("screen", 100);

        ProcessInstanceEvent instance = start("app-inc-1", "AE");

        assertThat(instance).isActive().hasActiveElements("Task_Screen");
        assertThat(instance).hasActiveIncidents();
        assertThat(SIM.accountsOpened()).isZero();
    }

    @Test
    void theInstanceCarriesOnlyIdsAndEnumsUnderTheSizeLimit() {
        ProcessInstanceEvent instance = start("app-vars-1", "TR");

        assertThat(instance).isCompleted();
        var variables = client.newVariableSearchRequest().filter((f) -> f.processInstanceKey(instance.getProcessInstanceKey()))
            .send().join().items().stream()
            // Task-local input mappings (purpose) are not instance state; only the instance's own scope counts.
            .filter((v) -> v.getScopeKey() == instance.getProcessInstanceKey()).toList();
        assertThat(variables).extracting((v) -> v.getName()).doesNotContain("amount", "name", "email", "iban");
        assertThat(variables).extracting((v) -> v.getName())
            .containsOnly("applicationId", "jurisdiction", "kycStatus", "screeningId", "screeningDecision", "accountId", "aliasOfferStatus", "notificationRef");
        int bytes = variables.stream().mapToInt((v) -> v.getValue().length()).sum();
        assertThat(bytes).isLessThan(8 * 1024);
    }
}
