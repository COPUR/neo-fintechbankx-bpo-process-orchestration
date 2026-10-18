package com.bank.bpo.payments;

import com.bank.bpo.payments.adapter.AccHoldAdapter;
import com.bank.bpo.payments.adapter.NtfNotificationAdapter;
import com.bank.bpo.payments.adapter.PayExceptionAdapter;
import com.bank.bpo.payments.adapter.PsgTrSchemeStatusAdapter;
import com.bank.bpo.payments.port.HoldPort;
import com.bank.bpo.payments.port.NotificationPort;
import com.bank.bpo.payments.port.PaymentExceptionPort;
import com.bank.bpo.payments.port.SchemeStatusPort;
import com.bank.bpo.workerkit.ClientCredentialsToken;
import com.bank.bpo.workerkit.ProviderHttp;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires each port to its provider, the same way as the onboarding family. Deployed, every provider and the token
 * endpoint are https and the token is a Keycloak client-credentials token for svc-bpo-worker-payments; a missing
 * endpoint, credential or scope stops the worker at start. Only the {@value #SIMULATOR_PROFILE} profile uses a fixed
 * token, and then every URL must be plain http on the loopback address, so the fixed token can never leave the pod.
 */
@Configuration
class ProviderConfiguration {

    static final String SIMULATOR_PROFILE = "simulator";

    private final boolean simulator;

    ProviderConfiguration(Environment environment) {
        this.simulator = environment.matchesProfiles(SIMULATOR_PROFILE);
    }

    @Bean
    Supplier<String> workerToken(@Value("${bpo.auth.token-uri:}") String tokenUri,
                                 @Value("${bpo.auth.client-id:svc-bpo-worker-payments}") String clientId,
                                 @Value("${bpo.auth.client-credential:}") String clientCredential,
                                 @Value("${bpo.auth.scope:}") String scope) {
        if (simulator) {
            return () -> "simulator-token";
        }
        if (tokenUri.isBlank()) {
            throw new IllegalStateException("bpo.auth.token-uri is required outside the simulator profile");
        }
        URI uri = URI.create(tokenUri);
        if (!"https".equals(uri.getScheme())) {
            throw new IllegalStateException("bpo.auth.token-uri must be https");
        }
        return new ClientCredentialsToken(uri, clientId, clientCredential, scope, Clock.systemUTC());
    }

    private ProviderHttp http(String baseUrl, Supplier<String> token) {
        URI uri = URI.create(baseUrl);
        Duration timeout = Duration.ofSeconds(5);
        return simulator ? ProviderHttp.loopback(uri, token, timeout) : ProviderHttp.https(uri, token, timeout);
    }

    @Bean
    SchemeStatusPort schemeStatusPort(@Value("${bpo.providers.psg-tr.base-url}") String url, Supplier<String> workerToken) {
        return new PsgTrSchemeStatusAdapter(http(url, workerToken));
    }

    @Bean
    HoldPort holdPort(@Value("${bpo.providers.acc.base-url}") String url, Supplier<String> workerToken) {
        return new AccHoldAdapter(http(url, workerToken));
    }

    @Bean
    PaymentExceptionPort paymentExceptionPort(@Value("${bpo.providers.pay.base-url}") String url, Supplier<String> workerToken) {
        return new PayExceptionAdapter(http(url, workerToken));
    }

    @Bean
    NotificationPort notificationPort(@Value("${bpo.providers.ntf.base-url}") String url, Supplier<String> workerToken) {
        return new NtfNotificationAdapter(http(url, workerToken));
    }
}
