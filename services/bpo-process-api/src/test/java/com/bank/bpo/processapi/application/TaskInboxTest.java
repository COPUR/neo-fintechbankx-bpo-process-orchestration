package com.bank.bpo.processapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.bpo.processapi.FakeUserTasks;
import com.bank.bpo.processapi.application.port.TaskEvents;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.StaffUser;
import com.bank.bpo.processapi.domain.TaskRefusal;
import com.bank.bpo.processapi.domain.TaskRefusal.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskInboxTest {

    private static final String LIB = "lib-screening-review";
    private static final StaffUser ANALYST = new StaffUser("ayse.analyst", Set.of("cmp-tr-onboarding-l1"));
    private static final StaffUser OTHER_ANALYST = new StaffUser("omer.analyst", Set.of("cmp-tr-onboarding-l1"));
    private static final StaffUser AE_ANALYST = new StaffUser("ali.analyst", Set.of("cmp-ae-onboarding-l1"));
    private static final StaffUser OFFICER = new StaffUser("selin.officer", Set.of("cmp-tr-compliance-officer"));
    /** Holds both queues, so could be maker and checker of one case. */
    private static final StaffUser BOTH = new StaffUser("deniz.both", Set.of("cmp-tr-onboarding-l1", "cmp-tr-compliance-officer"));

    private FakeUserTasks engine;
    private TaskInbox inbox;

    @BeforeEach
    void setUp() {
        engine = new FakeUserTasks();
        inbox = new TaskInbox(engine, new QueueGroups(Map.of(
            Actor.COMPLIANCE_ANALYST, Set.of("cmp-ae-onboarding-l1", "cmp-tr-onboarding-l1", "cmp-review-l1"),
            Actor.COMPLIANCE_SENIOR, Set.of("cmp-ae-mlro", "cmp-tr-compliance-officer", "cmp-review-l2"),
            Actor.PAYMENTS_OPERATIONS, Set.of("pay-tr-fast-ops", "pay-ops-l1"),
            Actor.PAYMENTS_SUPERVISOR, Set.of("pay-tr-fast-supervisor", "pay-ops-l2"))));
        engine.add(1, LIB, "Task_AnalystReview", null, 100, "cmp-tr-onboarding-l1");
        engine.add(2, LIB, "Task_AnalystReview", null, 200, "cmp-ae-onboarding-l1");
        engine.add(3, LIB, "Task_SeniorReview", null, 300, "cmp-tr-compliance-officer");
        engine.add(4, "acc-tpp-account-opening", "Task_Unknown", null, 400, "cmp-tr-onboarding-l1");
        engine.variables.put(1L, Map.of("caseId", "CASE-1"));
    }

    @Test
    void listsOnlyOpenTasksOfTheActorInTheUsersQueuesDueFirst() {
        engine.add(5, LIB, "Task_AnalystReview", "omer.analyst", 500, "cmp-tr-onboarding-l1");

        assertThat(inbox.list(Actor.COMPLIANCE_ANALYST, ANALYST))
            .extracting(TaskView::taskKey).containsExactly(5L, 1L);
        assertThat(inbox.list(Actor.COMPLIANCE_ANALYST, ANALYST).get(0).assignment()).isEqualTo(TaskView.Assignment.OTHER);
        assertThat(inbox.list(Actor.COMPLIANCE_ANALYST, AE_ANALYST)).extracting(TaskView::taskKey).containsExactly(2L);
        assertThat(inbox.list(Actor.COMPLIANCE_SENIOR, OFFICER)).extracting(TaskView::taskKey).containsExactly(3L);
    }

    @Test
    void anActorTheUserDoesNotHoldIsRefused() {
        assertRefused(() -> inbox.list(Actor.COMPLIANCE_SENIOR, ANALYST), Reason.NOT_YOUR_ACTOR);
        assertThat(inbox.actorsOf(ANALYST)).containsExactly(Actor.COMPLIANCE_ANALYST);
        assertThat(inbox.actorsOf(BOTH)).containsExactly(Actor.COMPLIANCE_ANALYST, Actor.COMPLIANCE_SENIOR);
    }

    @Test
    void aTaskOutsideTheUsersQueuesActorOrKindLooksLikeNoTask() {
        assertRefused(() -> inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 2), Reason.TASK_NOT_FOUND);
        assertRefused(() -> inbox.read(Actor.COMPLIANCE_ANALYST, BOTH, 3), Reason.TASK_NOT_FOUND);
        assertRefused(() -> inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 4), Reason.TASK_NOT_FOUND);
        assertRefused(() -> inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 99), Reason.TASK_NOT_FOUND);
        assertThat(engine.writes).isEmpty();
    }

    @Test
    void readShowsTheCaseReferenceReadOnly() {
        TaskView view = inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 1);

        assertThat(view.referenceName()).isEqualTo("caseId");
        assertThat(view.reference()).isEqualTo("CASE-1");
        assertThat(view.decisions()).containsExactly("CLEAR", "REJECT", "ESCALATE");
    }

    @Test
    void aReferenceThatIsNotAPlainIdIsNotShown() {
        engine.variables.put(1L, Map.of("caseId", "../admin"));

        assertThat(inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 1).reference()).isNull();
    }

    @Test
    void claimIsIdempotentForTheHolderAndRefusedForOthers() {
        assertThat(inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1).assignment()).isEqualTo(TaskView.Assignment.MINE);
        inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);

        assertRefused(() -> inbox.claim(Actor.COMPLIANCE_ANALYST, OTHER_ANALYST, 1), Reason.TASK_CLAIMED_BY_OTHER);
        assertThat(engine.writes).containsExactly("assign 1 ayse.analyst");
    }

    @Test
    void onlyTheHolderReleases() {
        inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);

        assertRefused(() -> inbox.release(Actor.COMPLIANCE_ANALYST, OTHER_ANALYST, 1), Reason.TASK_NOT_ASSIGNED_TO_YOU);
        inbox.release(Actor.COMPLIANCE_ANALYST, ANALYST, 1);
        assertThat(engine.tasks.get(1L).assignee()).isNull();
    }

    @Test
    void aDecisionWritesTheDecisionVariableAndNothingElse() {
        inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);

        inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "ESCALATE"));

        assertThat(engine.completions.get(1L)).containsExactly(Map.entry("reviewDecision", "ESCALATE"));
    }

    @Test
    void aBodyCarryingAnIdOrAnyOtherFieldIsRefusedBeforeTheEngineIsTouched() {
        inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);
        Map<String, Object> withId = new LinkedHashMap<>();
        withId.put("decision", "CLEAR");
        withId.put("caseId", "CASE-OTHER");

        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, withId), Reason.BODY_NOT_ALLOWED);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("reviewDecision", "CLEAR")), Reason.BODY_NOT_ALLOWED);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", 1)), Reason.BODY_NOT_ALLOWED);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of()), Reason.BODY_NOT_ALLOWED);
        assertThat(engine.completions).isEmpty();
    }

    @Test
    void aDecisionNeedsTheClaimAndAValueTheFormOffers() {
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "CLEAR")), Reason.TASK_NOT_ASSIGNED_TO_YOU);
        inbox.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "EXPIRED")), Reason.DECISION_NOT_ALLOWED);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "clear")), Reason.DECISION_NOT_ALLOWED);
        assertThat(engine.completions).isEmpty();
    }

    @Test
    void theSeniorCannotEscalateAgain() {
        inbox.claim(Actor.COMPLIANCE_SENIOR, OFFICER, 3);

        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_SENIOR, OFFICER, 3, Map.of("decision", "ESCALATE")), Reason.DECISION_NOT_ALLOWED);
    }

    @Test
    void whoEscalatedACaseCannotDecideItAsSenior() {
        engine.add(10, LIB, "Task_AnalystReview", null, 900, "cmp-tr-onboarding-l1");
        inbox.claim(Actor.COMPLIANCE_ANALYST, BOTH, 10);
        inbox.decide(Actor.COMPLIANCE_ANALYST, BOTH, 10, Map.of("decision", "ESCALATE"));
        engine.add(11, LIB, "Task_SeniorReview", null, 900, "cmp-tr-compliance-officer");
        inbox.claim(Actor.COMPLIANCE_SENIOR, BOTH, 11);

        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_SENIOR, BOTH, 11, Map.of("decision", "CLEAR")), Reason.FOUR_EYES_REQUIRED);
        assertThat(engine.completions).doesNotContainKey(11L);

        inbox.release(Actor.COMPLIANCE_SENIOR, BOTH, 11);
        assertThat(inbox.claim(Actor.COMPLIANCE_SENIOR, OFFICER, 11).fourEyesBlocked()).isFalse();
        inbox.decide(Actor.COMPLIANCE_SENIOR, OFFICER, 11, Map.of("decision", "CLEAR"));
        assertThat(engine.completions.get(11L)).containsExactly(Map.entry("reviewDecision", "CLEAR"));
    }

    @Test
    void lettingTheMakerTaskExpireDoesNotOpenTheCheckerTaskToTheSamePerson() {
        // DevSecOps S-1 on 0ef1e8d: claim the analyst task, let its SLA expire, then decide the senior task alone.
        engine.add(30, LIB, "Task_AnalystReview", null, 3000, "cmp-tr-onboarding-l1");
        inbox.claim(Actor.COMPLIANCE_ANALYST, BOTH, 30);
        engine.expire(30);
        engine.add(31, LIB, "Task_SeniorReview", null, 3000, "cmp-tr-compliance-officer");
        assertThat(inbox.claim(Actor.COMPLIANCE_SENIOR, BOTH, 31).fourEyesBlocked()).isTrue();
        assertThat(inbox.list(Actor.COMPLIANCE_SENIOR, BOTH)).allMatch(v -> !v.fourEyesBlocked());

        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_SENIOR, BOTH, 31, Map.of("decision", "CLEAR")), Reason.FOUR_EYES_REQUIRED);
        assertThat(engine.completions).doesNotContainKey(31L);
    }

    @Test
    void claimsDecisionsAndRefusalsAreReportedWithoutIdsOrNames() {
        List<String> seen = new ArrayList<>();
        TaskEvents events = new TaskEvents() {
            public void claimed(Actor a, String t) { seen.add("claimed " + a.id() + " " + t); }
            public void released(Actor a, String t) { seen.add("released " + a.id() + " " + t); }
            public void decided(Actor a, String t, String d, Duration age, boolean late) {
                seen.add("decided " + a.id() + " " + t + " " + d + " " + age + " late=" + late);
            }
            public void refused(Actor a, Reason r) { seen.add("refused " + a.id() + " " + r); }
        };
        TaskInbox measured = new TaskInbox(engine, new QueueGroups(Map.of(Actor.COMPLIANCE_ANALYST, Set.of("cmp-tr-onboarding-l1"))),
            events, Clock.fixed(Instant.parse("2026-10-18T15:00:00Z"), ZoneOffset.UTC));

        measured.claim(Actor.COMPLIANCE_ANALYST, ANALYST, 1);
        assertThatThrownBy(() -> measured.claim(Actor.COMPLIANCE_ANALYST, OTHER_ANALYST, 1)).isInstanceOf(TaskRefusal.class);
        measured.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "CLEAR"));

        // Task 1 was created 10:00:01 and due 13:59:59 (FakeUserTasks), decided at 15:00.
        assertThat(seen).containsExactly(
            "claimed compliance-analyst Task_AnalystReview",
            "refused compliance-analyst TASK_CLAIMED_BY_OTHER",
            "decided compliance-analyst Task_AnalystReview CLEAR PT4H59M59S late=true");
    }

    @Test
    void aSeniorTaskAfterAnExpiredAnalystSlaHasNoMakerAndCanBeDecided() {
        inbox.claim(Actor.COMPLIANCE_SENIOR, BOTH, 3);

        inbox.decide(Actor.COMPLIANCE_SENIOR, BOTH, 3, Map.of("decision", "REJECT"));

        assertThat(engine.completions.get(3L)).containsExactly(Map.entry("reviewDecision", "REJECT"));
    }

    @Test
    void aPaymentExceptionWritesOnlyTheExceptionDecisionAndTheSupervisorIsASecondPerson() {
        StaffUser ops = new StaffUser("emre.ops", Set.of("pay-tr-fast-ops", "pay-tr-fast-supervisor"));
        StaffUser supervisor = new StaffUser("zeynep.sup", Set.of("pay-tr-fast-supervisor"));
        engine.add(20, "pay-instant-exception", "Task_OpsReview", null, 2000, "pay-tr-fast-ops");
        engine.variables.put(20L, Map.of("paymentId", "PAY-1"));

        assertThat(inbox.read(Actor.PAYMENTS_OPERATIONS, ops, 20).reference()).isEqualTo("PAY-1");
        assertRefused(() -> inbox.read(Actor.COMPLIANCE_ANALYST, ANALYST, 20), Reason.TASK_NOT_FOUND);
        inbox.claim(Actor.PAYMENTS_OPERATIONS, ops, 20);
        assertRefused(() -> inbox.decide(Actor.PAYMENTS_OPERATIONS, ops, 20, Map.of("decision", "CLEAR")), Reason.DECISION_NOT_ALLOWED);
        assertRefused(() -> inbox.decide(Actor.PAYMENTS_OPERATIONS, ops, 20,
            new LinkedHashMap<>(Map.of("decision", "SETTLED", "paymentId", "PAY-OTHER"))), Reason.BODY_NOT_ALLOWED);
        inbox.decide(Actor.PAYMENTS_OPERATIONS, ops, 20, Map.of("decision", "ESCALATE"));
        assertThat(engine.completions.get(20L)).containsExactly(Map.entry("exceptionDecision", "ESCALATE"));

        engine.add(21, "pay-instant-exception", "Task_SupervisorReview", null, 2000, "pay-tr-fast-supervisor");
        inbox.claim(Actor.PAYMENTS_SUPERVISOR, ops, 21);
        assertRefused(() -> inbox.decide(Actor.PAYMENTS_SUPERVISOR, ops, 21, Map.of("decision", "SETTLED")), Reason.FOUR_EYES_REQUIRED);
        inbox.release(Actor.PAYMENTS_SUPERVISOR, ops, 21);
        inbox.claim(Actor.PAYMENTS_SUPERVISOR, supervisor, 21);
        assertRefused(() -> inbox.decide(Actor.PAYMENTS_SUPERVISOR, supervisor, 21, Map.of("decision", "ESCALATE")), Reason.DECISION_NOT_ALLOWED);
        inbox.decide(Actor.PAYMENTS_SUPERVISOR, supervisor, 21, Map.of("decision", "SETTLED"));
        assertThat(engine.completions.get(21L)).containsExactly(Map.entry("exceptionDecision", "SETTLED"));
    }

    @Test
    void anEngineThatDoesNotAnswerFailsClosed() {
        engine.down = true;

        assertRefused(() -> inbox.list(Actor.COMPLIANCE_ANALYST, ANALYST), Reason.ENGINE_UNAVAILABLE);
        assertRefused(() -> inbox.decide(Actor.COMPLIANCE_ANALYST, ANALYST, 1, Map.of("decision", "CLEAR")), Reason.ENGINE_UNAVAILABLE);
    }

    private static void assertRefused(Runnable call, Reason reason) {
        assertThatThrownBy(call::run).isInstanceOf(TaskRefusal.class)
            .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(reason));
    }
}
