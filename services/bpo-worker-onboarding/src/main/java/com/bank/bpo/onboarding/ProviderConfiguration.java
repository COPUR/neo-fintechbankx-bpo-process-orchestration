package com.bank.bpo.onboarding;

import com.bank.bpo.onboarding.adapter.AccApplicationAdapter;
import com.bank.bpo.onboarding.adapter.CmpReviewCaseAdapter;
import com.bank.bpo.onboarding.adapter.CmpScreeningAdapter;
import com.bank.bpo.onboarding.adapter.CusKycAdapter;
import com.bank.bpo.onboarding.adapter.NtfNotificationAdapter;
import com.bank.bpo.onboarding.adapter.PsgTrAliasAdapter;
import com.bank.bpo.onboarding.port.AccountApplicationPort;
import com.bank.bpo.onboarding.port.AliasOfferPort;
import com.bank.bpo.onboarding.port.KycPort;
import com.bank.bpo.onboarding.port.NotificationPort;
import com.bank.bpo.onboarding.port.ReviewCasePort;
import com.bank.bpo.onboarding.port.ScreeningPort;
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
 * Wires each port to its provider. Deployed, every provider and the token endpoint are https and the token is a
 * Keycloak client-credentials token for svc-bpo-worker-onboarding; a missing endpoint, credential or scope stops
 * the worker at start. Only the {@value #SIMULATOR_PROFILE} profile uses a fixed token, and then every URL must be
 * plain http on the loopback address, so the fixed token can never leave the pod.
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
                                 @Value("${bpo.auth.client-id:svc-bpo-worker-onboarding}") String clientId,
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
    KycPort kycPort(@Value("${bpo.providers.cus.base-url}") String url, Supplier<String> workerToken) {
        return new CusKycAdapter(http(url, workerToken));
    }

    @Bean
    ScreeningPort screeningPort(@Value("${bpo.providers.cmp.base-url}") String url, Supplier<String> workerToken) {
        return new CmpScreeningAdapter(http(url, workerToken));
    }

    @Bean
    ReviewCasePort reviewCasePort(@Value("${bpo.providers.cmp.base-url}") String url, Supplier<String> workerToken) {
        return new CmpReviewCaseAdapter(http(url, workerToken));
    }

    @Bean
    AccountApplicationPort accountApplicationPort(@Value("${bpo.providers.acc.base-url}") String url, Supplier<String> workerToken) {
        return new AccApplicationAdapter(http(url, workerToken));
    }

    @Bean
    AliasOfferPort aliasOfferPort(@Value("${bpo.providers.psg-tr.base-url}") String url, Supplier<String> workerToken) {
        return new PsgTrAliasAdapter(http(url, workerToken));
    }

    @Bean
    NotificationPort notificationPort(@Value("${bpo.providers.ntf.base-url}") String url, Supplier<String> workerToken) {
        return new NtfNotificationAdapter(http(url, workerToken));
    }
}
