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

/**
 * Wires each port to its provider. The base URLs name the domain services (or the simulator); the token is
 * a Keycloak client-credentials token for svc-bpo-worker-onboarding, or a fixed dev token when no token
 * endpoint is configured (dev and simulator profiles only; a deployed chart always sets the endpoint).
 */
@Configuration
class ProviderConfiguration {

    @Bean
    Supplier<String> workerToken(@Value("${bpo.auth.token-uri:}") String tokenUri,
                                 @Value("${bpo.auth.client-id:svc-bpo-worker-onboarding}") String clientId,
                                 @Value("${bpo.auth.client-credential:}") String clientCredential,
                                 @Value("${bpo.auth.scope:}") String scope) {
        if (tokenUri.isBlank()) {
            return () -> "dev-token";
        }
        return new ClientCredentialsToken(URI.create(tokenUri), clientId, clientCredential, scope, Clock.systemUTC());
    }

    private ProviderHttp http(String baseUrl, Supplier<String> token) {
        return new ProviderHttp(URI.create(baseUrl), token, Duration.ofSeconds(5));
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
