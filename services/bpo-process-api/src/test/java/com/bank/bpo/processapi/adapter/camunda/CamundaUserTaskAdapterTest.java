package com.bank.bpo.processapi.adapter.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.bpo.processapi.domain.EngineTask;
import com.bank.bpo.processapi.domain.TaskRefusal;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The adapter against a stub of the Orchestration Cluster REST API v2: paths, bodies, parsing, fail-closed. */
class CamundaUserTaskAdapterTest {

    private static final String TASK = """
        {"userTaskKey":"2251799813685301","processDefinitionId":"lib-screening-review","elementId":"Task_AnalystReview",
         "state":"CREATED","assignee":null,"candidateGroups":["cmp-tr-onboarding-l1"],
         "processInstanceKey":"2251799813685250","creationDate":"2026-10-18T10:00:00.000Z",
         "dueDate":"2026-10-18T14:00:00.000Z"}""";

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private int status = 200;
    private String body = "{}";
    private CamundaUserTaskAdapter adapter;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                + exchange.getRequestHeaders().getFirst("authorization") + " " + sent);
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(status, out.length);
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
        adapter = new CamundaUserTaskAdapter(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), () -> "svc-token");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void searchesOpenTasksOfOneElementInTheGivenGroupsWithTheServiceToken() {
        body = "{\"items\":[" + TASK + "],\"page\":{\"totalItems\":1}}";

        List<EngineTask> tasks = adapter.openTasks("lib-screening-review", "Task_AnalystReview", Set.of("cmp-tr-onboarding-l1"), 200);

        assertThat(tasks).singleElement().satisfies(t -> {
            assertThat(t.key()).isEqualTo(2251799813685301L);
            assertThat(t.processInstanceKey()).isEqualTo(2251799813685250L);
            assertThat(t.assignee()).isNull();
            assertThat(t.candidateGroups()).containsExactly("cmp-tr-onboarding-l1");
            assertThat(t.dueDate()).hasToString("2026-10-18T14:00:00Z");
        });
        assertThat(requests).singleElement().asString()
            .startsWith("POST /v2/user-tasks/search Bearer svc-token ")
            .contains("\"state\":\"CREATED\"", "\"processDefinitionId\":\"lib-screening-review\"",
                "\"elementId\":\"Task_AnalystReview\"", "\"candidateGroup\":{\"$in\":[\"cmp-tr-onboarding-l1\"]}",
                "\"limit\":200");
    }

    @Test
    void anUnknownTaskIsEmpty() {
        status = 404;
        body = "{\"title\":\"NOT_FOUND\"}";

        assertThat(adapter.find(5)).isEmpty();
        assertThat(requests).singleElement().asString().startsWith("GET /v2/user-tasks/5 ");
    }

    @Test
    void readsAStringVariableDecodedFromItsJsonValue() {
        body = "{\"items\":[{\"name\":\"caseId\",\"value\":\"\\\"CASE-1\\\"\",\"isTruncated\":false}]}";

        assertThat(adapter.stringVariable(7, "caseId")).contains("CASE-1");
        assertThat(requests).singleElement().asString()
            .startsWith("POST /v2/user-tasks/7/variables/search ").contains("{\"filter\":{\"name\":\"caseId\"}}");
    }

    @Test
    void aTruncatedVariableFailsClosed() {
        body = "{\"items\":[{\"name\":\"caseId\",\"value\":\"\\\"CASE\",\"isTruncated\":true}]}";

        assertThatThrownBy(() -> adapter.stringVariable(7, "caseId")).isInstanceOf(TaskRefusal.class);
    }

    @Test
    void claimsWithoutOverrideAndMapsAConflict() {
        status = 204;
        adapter.assign(7, "ayse.analyst");
        assertThat(requests.get(0)).startsWith("POST /v2/user-tasks/7/assignment ")
            .contains("\"assignee\":\"ayse.analyst\"", "\"allowOverride\":false");

        status = 409;
        body = "{\"title\":\"INVALID_STATE\"}";
        assertThatThrownBy(() -> adapter.assign(7, "omer.analyst")).isInstanceOf(TaskRefusal.class)
            .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.TASK_CLAIMED_BY_OTHER));
    }

    @Test
    void releasesAndCompletesWithExactlyTheGivenVariables() {
        status = 204;

        adapter.unassign(7);
        adapter.complete(7, Map.of("reviewDecision", "CLEAR"));

        assertThat(requests.get(0)).startsWith("DELETE /v2/user-tasks/7/assignee ");
        assertThat(requests.get(1)).startsWith("POST /v2/user-tasks/7/completion ")
            .endsWith("{\"variables\":{\"reviewDecision\":\"CLEAR\"}}");
    }

    @Test
    void findsEveryoneWhoHeldTheMakerTaskOfTheSameInstanceInAnyState() {
        body = "{\"items\":[{\"userTaskKey\":\"1\",\"processDefinitionId\":\"lib-screening-review\",\"elementId\":\"Task_AnalystReview\","
            + "\"state\":\"COMPLETED\",\"assignee\":\"deniz.both\",\"candidateGroups\":[],\"processInstanceKey\":\"9\"}]}";

        assertThat(adapter.assigneesOf(9, "Task_AnalystReview")).containsExactly("deniz.both");
        assertThat(requests).singleElement().asString()
            .contains("\"processInstanceKey\":\"9\"", "\"elementId\":\"Task_AnalystReview\"")
            .doesNotContain("\"state\"");
    }

    @Test
    void serverErrorsAuthErrorsAndUnreadableBodiesFailClosed() {
        for (int s : new int[] {500, 503, 401, 403}) {
            status = s;
            assertThatThrownBy(() -> adapter.find(7)).isInstanceOf(TaskRefusal.class)
                .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.ENGINE_UNAVAILABLE));
        }
        status = 200;
        body = "not json";
        assertThatThrownBy(() -> adapter.find(7)).isInstanceOf(TaskRefusal.class);
        body = "{\"items\":{}}";
        assertThatThrownBy(() -> adapter.openTasks("p", "e", Set.of("g"), 1)).isInstanceOf(TaskRefusal.class);
        body = "{\"userTaskKey\":\"x\",\"processDefinitionId\":\"p\",\"elementId\":\"e\",\"state\":\"CREATED\"}";
        assertThatThrownBy(() -> adapter.find(7)).isInstanceOf(TaskRefusal.class);
        status = 204;
        body = "";
        org.assertj.core.api.Assertions.assertThatCode(() -> adapter.complete(7, Map.of())).doesNotThrowAnyException();
        status = 200;
        assertThatThrownBy(() -> adapter.complete(7, Map.of())).isInstanceOf(TaskRefusal.class);
    }

    @Test
    void aWriteWhoseAnswerIsLostOrIsAServerErrorHasAnUnknownOutcome() {
        for (int s : new int[] {500, 502, 503}) {
            status = s;
            body = "{}";
            assertThatThrownBy(() -> adapter.complete(7, Map.of("reviewDecision", "CLEAR"))).isInstanceOf(TaskRefusal.class)
                .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.OUTCOME_UNKNOWN));
        }
        status = 400;
        assertThatThrownBy(() -> adapter.complete(7, Map.of())).isInstanceOf(TaskRefusal.class)
            .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.ENGINE_UNAVAILABLE));
    }

    @Test
    void aWriteThatTimesOutAfterSendingHasAnUnknownOutcome() throws IOException {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(7_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();
        try {
            CamundaUserTaskAdapter slowAdapter = new CamundaUserTaskAdapter(
                URI.create("http://127.0.0.1:" + slow.getAddress().getPort()), () -> "svc-token");
            assertThatThrownBy(() -> slowAdapter.complete(7, Map.of("reviewDecision", "CLEAR"))).isInstanceOf(TaskRefusal.class)
                .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.OUTCOME_UNKNOWN));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void aWriteToAnEngineThatRefusesTheConnectionSentNothing() {
        server.stop(0);

        assertThatThrownBy(() -> adapter.complete(7, Map.of())).isInstanceOf(TaskRefusal.class)
            .satisfies(e -> assertThat(((TaskRefusal) e).reason()).isEqualTo(TaskRefusal.Reason.ENGINE_UNAVAILABLE));
    }

    @Test
    void anUnreachableEngineFailsClosed() {
        server.stop(0);

        assertThatThrownBy(() -> adapter.find(7)).isInstanceOf(TaskRefusal.class);
    }

    @Test
    void plainHttpIsOnlyAllowedOnLoopback() {
        assertThatThrownBy(() -> new CamundaUserTaskAdapter(URI.create("http://camunda.example"), () -> "t"))
            .isInstanceOf(IllegalArgumentException.class);
        new CamundaUserTaskAdapter(URI.create("https://camunda.example/"), () -> "t");
    }
}
