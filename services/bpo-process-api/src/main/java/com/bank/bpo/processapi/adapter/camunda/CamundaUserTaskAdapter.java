package com.bank.bpo.processapi.adapter.camunda;

import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.ENGINE_UNAVAILABLE;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.OUTCOME_UNKNOWN;
import static com.bank.bpo.processapi.domain.TaskRefusal.Reason.TASK_CLAIMED_BY_OTHER;

import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.domain.EngineTask;
import com.bank.bpo.processapi.domain.TaskRefusal;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * User tasks through the Orchestration Cluster REST API v2 (Camunda 8.10): {@code /v2/user-tasks/search},
 * {@code /v2/user-tasks/{key}}, {@code .../assignment}, {@code .../assignee}, {@code .../completion} and
 * {@code .../variables/search}. Paths and field names follow the request and result types of
 * camunda-client-java 8.10.4. The API calls as {@code svc-bpo-process} with a client-credentials token;
 * staff tokens never reach the engine.
 *
 * <p>Fail closed: on a read, a timeout, a 5xx, a 401/403 or an unreadable body is ENGINE_UNAVAILABLE. On a
 * write (assign, unassign, complete), only a refused connection proves nothing was sent; a timeout or a 5xx
 * after sending is OUTCOME_UNKNOWN, so the user is told to refresh rather than that nothing was recorded.
 */
public final class CamundaUserTaskAdapter implements UserTaskPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final URI base;
    private final Supplier<String> token;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public CamundaUserTaskAdapter(URI baseUrl, Supplier<String> token) {
        if (!"https".equals(baseUrl.getScheme()) && !("http".equals(baseUrl.getScheme()) && loopback(baseUrl.getHost()))) {
            throw new IllegalArgumentException("the engine URL must be https (plain http only on loopback, for tests)");
        }
        String s = baseUrl.toString();
        this.base = URI.create(s.endsWith("/") ? s : s + "/");
        this.token = token;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<EngineTask> openTasks(String processDefinitionId, String elementId, Set<String> groups, int limit) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("state", EngineTask.OPEN);
        filter.put("processDefinitionId", processDefinitionId);
        filter.put("elementId", elementId);
        filter.put("candidateGroup", Map.of("$in", List.copyOf(groups)));
        JsonNode body = call("POST", "v2/user-tasks/search", Map.of(
            "filter", filter,
            "page", Map.of("limit", limit),
            "sort", List.of(Map.of("field", "creationDate", "order", "ASC"))), 200);
        List<EngineTask> tasks = new ArrayList<>();
        for (JsonNode item : items(body)) {
            tasks.add(task(item));
        }
        return tasks;
    }

    @Override
    public Optional<EngineTask> find(long taskKey) {
        HttpResponse<String> response = send("GET", "v2/user-tasks/" + taskKey, null);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(task(read(response, 200)));
    }

    @Override
    public Optional<String> stringVariable(long taskKey, String name) {
        JsonNode body = call("POST", "v2/user-tasks/" + taskKey + "/variables/search",
            Map.of("filter", Map.of("name", name)), 200);
        for (JsonNode item : items(body)) {
            if (name.equals(item.path("name").asString(null))) {
                // Variable values come back JSON-encoded: a string variable is "\"CASE-1\"".
                String encoded = item.path("value").asString(null);
                if (encoded == null || item.path("isTruncated").asBoolean(false)) {
                    throw new TaskRefusal(ENGINE_UNAVAILABLE);
                }
                JsonNode value = parse(encoded);
                return value.isString() ? Optional.of(value.asString()) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public void assign(long taskKey, String assignee) {
        HttpResponse<String> response = write("POST", "v2/user-tasks/" + taskKey + "/assignment",
            Map.of("assignee", assignee, "allowOverride", false));
        if (response.statusCode() == 409) {
            throw new TaskRefusal(TASK_CLAIMED_BY_OTHER);
        }
        expectWritten(response);
    }

    @Override
    public void unassign(long taskKey) {
        expectWritten(write("DELETE", "v2/user-tasks/" + taskKey + "/assignee", null));
    }

    @Override
    public void complete(long taskKey, Map<String, String> variables) {
        expectWritten(write("POST", "v2/user-tasks/" + taskKey + "/completion", Map.of("variables", variables)));
    }

    @Override
    public Set<String> assigneesOf(long processInstanceKey, String elementId) {
        // No state filter: a maker task cancelled by its SLA timer keeps the assignee who let it expire.
        JsonNode body = call("POST", "v2/user-tasks/search", Map.of(
            "filter", Map.of("processInstanceKey", Long.toString(processInstanceKey), "elementId", elementId),
            "page", Map.of("limit", 50)), 200);
        Set<String> assignees = new LinkedHashSet<>();
        for (JsonNode item : items(body)) {
            String assignee = item.path("assignee").asString(null);
            if (assignee != null) {
                assignees.add(assignee);
            }
        }
        return assignees;
    }

    /** A write: a refused connection is ENGINE_UNAVAILABLE (nothing sent); any later failure is OUTCOME_UNKNOWN. */
    private HttpResponse<String> write(String method, String path, Object body) {
        try {
            return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        } catch (IOException e) {
            throw new TaskRefusal(OUTCOME_UNKNOWN);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskRefusal(OUTCOME_UNKNOWN);
        } catch (RuntimeException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private static void expectWritten(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 204) {
            return;
        }
        // 5xx and 429 come from a server that may have applied the write; 4xx refused it before applying.
        throw new TaskRefusal(status >= 500 || status == 429 ? OUTCOME_UNKNOWN : ENGINE_UNAVAILABLE);
    }

    private JsonNode call(String method, String path, Object body, int expected) {
        return read(send(method, path, body), expected);
    }

    private HttpRequest request(String method, String path, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(TIMEOUT)
            .header("authorization", "Bearer " + token.get())
            .header("accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("content-type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        return request.build();
    }

    private HttpResponse<String> send(String method, String path, Object body) {
        try {
            return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | RuntimeException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private JsonNode read(HttpResponse<String> response, int expected) {
        expect(response, expected);
        JsonNode node = parse(response.body());
        if (!node.isObject()) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
        return node;
    }

    private static void expect(HttpResponse<String> response, int expected) {
        if (response.statusCode() != expected) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(text == null ? "" : text);
        } catch (RuntimeException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private static Iterable<JsonNode> items(JsonNode body) {
        JsonNode items = body.path("items");
        if (!items.isArray()) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
        return items;
    }

    private static EngineTask task(JsonNode item) {
        List<String> groups = new ArrayList<>();
        item.path("candidateGroups").forEach(g -> groups.add(g.asString()));
        String processDefinitionId = item.path("processDefinitionId").asString(null);
        String elementId = item.path("elementId").asString(null);
        String state = item.path("state").asString(null);
        if (processDefinitionId == null || elementId == null || state == null) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
        String assignee = item.path("assignee").asString(null);
        return new EngineTask(key(item.path("userTaskKey")), processDefinitionId, elementId, state,
            assignee == null || assignee.isBlank() ? null : assignee, groups, key(item.path("processInstanceKey")),
            instant(item.path("creationDate")), instant(item.path("dueDate")));
    }

    /** Keys are int64; REST v2 sends them as strings. */
    private static long key(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.asLong();
            }
            return Long.parseLong(node.asString(null));
        } catch (RuntimeException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private static Instant instant(JsonNode node) {
        String text = node.asString(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(text).toInstant();
        } catch (RuntimeException e) {
            throw new TaskRefusal(ENGINE_UNAVAILABLE);
        }
    }

    private static boolean loopback(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host);
    }
}
