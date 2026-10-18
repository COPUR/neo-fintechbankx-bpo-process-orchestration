package com.bank.bpo.workerkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.bpo.simulator.DomainSimulator;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The status mapping of 05 section 7, point 4, against a server that really answers each status. */
class ProviderHttpTest {

    private static DomainSimulator sim;
    private static ProviderHttp http;

    @BeforeAll
    static void start() {
        sim = DomainSimulator.start();
        http = new ProviderHttp(sim.baseUrl(), () -> "t", Duration.ofSeconds(3));
    }

    @AfterAll
    static void stop() {
        sim.close();
    }

    @BeforeEach
    void reset() {
        sim.reset();
    }

    private ProviderException.Kind kindOf(Runnable call) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(ProviderException.class, call::run).kind();
    }

    @Test
    void aGetReturnsTheBody() {
        sim.kyc("a1", "PENDING");
        assertThat(http.get("/api/v1/applications/a1/kyc")).containsEntry("kycStatus", "PENDING");
    }

    @Test
    void thePostSendsTheBearerTokenAndTheIdempotencyHeader() {
        http.post("/api/v1/account-applications/a1/open", Map.of(), "a1:open");
        assertThat(sim.callsTo("/api/v1/account-applications/a1/open").get(0).idempotencyKey()).isEqualTo("a1:open");
    }

    @Test
    void aServerErrorIsTransient() {
        sim.failBeforeEffect("kyc-status", 1);
        assertThat(kindOf(() -> http.get("/api/v1/applications/a1/kyc"))).isEqualTo(ProviderException.Kind.TRANSIENT);
    }

    @Test
    void aConcurrentUpdateConflictIsTransient() {
        sim.failWith("kyc-status", 409, "CONCURRENT_UPDATE", 1);
        assertThat(kindOf(() -> http.get("/api/v1/applications/a1/kyc"))).isEqualTo(ProviderException.Kind.TRANSIENT);
    }

    @Test
    void aBusinessRejectionIsNotRetried() {
        sim.failWith("open-account", 422, "ACCOUNT_NOT_OPERATIONAL", 1);
        ProviderException e = org.assertj.core.api.Assertions.catchThrowableOfType(ProviderException.class,
            () -> http.post("/api/v1/account-applications/a1/open", Map.of(), "a1:open"));
        assertThat(e.kind()).isEqualTo(ProviderException.Kind.BUSINESS_REJECTION);
        assertThat(e.code()).isEqualTo("ACCOUNT_NOT_OPERATIONAL");
    }

    @Test
    void aKeyReusedWithAnotherBodyIsAnIncidentNotARetry() {
        http.post("/api/v1/account-applications/a1/open", Map.of(), "a1:open");
        assertThat(kindOf(() -> http.post("/api/v1/account-applications/a1/open", Map.of("other", "body"), "a1:open")))
            .isEqualTo(ProviderException.Kind.KEY_REUSED);
    }

    @Test
    void anUnknownPathIsAContractFault() {
        assertThat(kindOf(() -> http.get("/api/v1/nowhere"))).isEqualTo(ProviderException.Kind.CONTRACT);
    }

    @Test
    void aMissingKeyOnAnEndpointThatRequiresOneIsAContractFault() {
        assertThat(kindOf(() -> http.post("/api/v1/account-applications/a1/open", Map.of(), null))).isEqualTo(ProviderException.Kind.CONTRACT);
    }

    @Test
    void anUnreachableServerIsTransient() {
        ProviderHttp dead = new ProviderHttp(java.net.URI.create("http://127.0.0.1:1/"), () -> "t", Duration.ofSeconds(1));
        assertThatThrownBy(() -> dead.get("/x")).isInstanceOfSatisfying(ProviderException.class,
            (e) -> assertThat(e.kind()).isEqualTo(ProviderException.Kind.TRANSIENT));
    }
}
