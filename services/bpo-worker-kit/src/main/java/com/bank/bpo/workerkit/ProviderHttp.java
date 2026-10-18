package com.bank.bpo.workerkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** JSON over HTTP to one domain service, with its bearer token and the idempotency header. */
public final class ProviderHttp {

    public static final String IDEMPOTENCY_HEADER = "x-idempotency-key";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final URI baseUrl;
    private final Supplier<String> token;
    private final Duration timeout;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public ProviderHttp(URI baseUrl, Supplier<String> token, Duration timeout) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.timeout = timeout;
    }

    public Map<String, Object> get(String path) {
        return send(request(path).GET().build());
    }

    public Map<String, Object> post(String path, Map<String, ?> body, String idempotencyKey) {
        HttpRequest.Builder request = request(path).header("content-type", "application/json");
        if (idempotencyKey != null) {
            request.header(IDEMPOTENCY_HEADER, idempotencyKey);
        }
        return send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUrl.resolve(path))
            .timeout(timeout)
            .header("authorization", "Bearer " + token.get())
            .header("accept", "application/json");
    }

    private Map<String, Object> send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "UNREACHABLE", request.uri().getPath() + " not reachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "INTERRUPTED", "interrupted", e);
        }
        int status = response.statusCode();
        Map<String, Object> body = parse(response.body());
        if (status >= 200 && status < 300) {
            return body;
        }
        String code = String.valueOf(body.getOrDefault("code", "HTTP_" + status));
        String message = request.uri().getPath() + " answered " + status + " " + code;
        if (status == 409 && "IDEMPOTENCY_KEY_REUSED".equals(code)) {
            throw new ProviderException(ProviderException.Kind.KEY_REUSED, code, message, null);
        }
        if (status == 422) {
            throw new ProviderException(ProviderException.Kind.BUSINESS_REJECTION, code, message, null);
        }
        if (status == 409 || status == 429 || status >= 500) {
            throw new ProviderException(ProviderException.Kind.TRANSIENT, code, message, null);
        }
        throw new ProviderException(ProviderException.Kind.CONTRACT, code, message, null);
    }

    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(body, MAP);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
