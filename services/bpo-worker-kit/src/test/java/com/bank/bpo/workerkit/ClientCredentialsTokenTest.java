package com.bank.bpo.workerkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.simulator.DomainSimulator;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClientCredentialsTokenTest {

    /** A clock the test moves. */
    private static final class Moving extends Clock {
        private Instant now = Instant.parse("2026-10-18T06:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    @Test
    void theTokenIsReusedUntilShortlyBeforeItExpires() {
        try (DomainSimulator sim = DomainSimulator.start()) {
            sim.tokenLifetimeSeconds(300);
            Moving clock = new Moving();
            ClientCredentialsToken token = new ClientCredentialsToken(sim.baseUrl().resolve("/token"), "svc-test", "not-a-real-credential", "aud-x", clock);

            assertThat(token.get()).isEqualTo("token-1");
            clock.advance(Duration.ofSeconds(200));
            assertThat(token.get()).isEqualTo("token-1");
            clock.advance(Duration.ofSeconds(80)); // 280 s: inside the 30 s margin before expiry
            assertThat(token.get()).isEqualTo("token-2");
            assertThat(sim.tokensIssued()).isEqualTo(2);
        }
    }

    @Test
    void theRequestNamesTheClientAndTheScope() {
        try (DomainSimulator sim = DomainSimulator.start()) {
            new ClientCredentialsToken(sim.baseUrl().resolve("/token"), "svc-bpo-worker-onboarding", "not-a-real-credential", "aud-svc-acc", Clock.systemUTC()).get();
            assertThat(sim.callsTo("/token")).hasSize(1);
            assertThat(sim.callsTo("/token").get(0).body())
                .containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", "svc-bpo-worker-onboarding")
                .containsEntry("scope", "aud-svc-acc");
        }
    }

    @Test
    void anUnreachableTokenEndpointIsTransient() {
        ClientCredentialsToken token = new ClientCredentialsToken(URI.create("http://127.0.0.1:1/token"), "c", "x", "s", Clock.systemUTC());
        org.assertj.core.api.Assertions.assertThatThrownBy(token::get).isInstanceOfSatisfying(ProviderException.class,
            (e) -> assertThat(e.kind()).isEqualTo(ProviderException.Kind.TRANSIENT));
    }
}
