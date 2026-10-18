package com.bank.bpo.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The onboarding rules (DevSecOps B-1) hold for the payments family too: no fixed token and no plain http outside the simulator. */
class ProviderConfigurationTest {

    private static ProviderConfiguration deployed() {
        return new ProviderConfiguration(new MockEnvironment());
    }

    private static ProviderConfiguration simulator() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ProviderConfiguration.SIMULATOR_PROFILE);
        return new ProviderConfiguration(environment);
    }

    @Test
    void aDeployedWorkerWithoutATokenEndpointDoesNotStart() {
        assertThatThrownBy(() -> deployed().workerToken("", "svc-bpo-worker-payments", "x", "aud"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("token-uri is required");
    }

    @Test
    void aDeployedWorkerRefusesAPlainHttpTokenEndpoint() {
        assertThatThrownBy(() -> deployed().workerToken("http://keycloak.bank/token", "svc-bpo-worker-payments", "x", "aud"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
    }

    @Test
    void aDeployedWorkerNeedsItsCredentialAndAScope() {
        assertThatThrownBy(() -> deployed().workerToken("https://keycloak.bank/token", "svc-bpo-worker-payments", "", "aud"))
            .hasMessageContaining("client credential");
        assertThatThrownBy(() -> deployed().workerToken("https://keycloak.bank/token", "svc-bpo-worker-payments", "x", ""))
            .hasMessageContaining("scope");
    }

    @Test
    void aDeployedWorkerRefusesAPlainHttpProvider() {
        Supplier<String> token = () -> "t";
        assertThatThrownBy(() -> deployed().holdPort("http://acc.bank/", token)).hasMessageContaining("https");
        assertThatThrownBy(() -> deployed().schemeStatusPort("http://psg-tr.bank/", token)).hasMessageContaining("https");
        assertThat(deployed().holdPort("https://acc.bank/", token)).isNotNull();
    }

    @Test
    void theSimulatorTokenOnlyTravelsToLoopback() {
        Supplier<String> token = simulator().workerToken("", "svc-bpo-worker-payments", "", "");
        assertThat(token.get()).isEqualTo("simulator-token");
        assertThatThrownBy(() -> simulator().paymentExceptionPort("http://pay.bank/", token)).hasMessageContaining("loopback");
        assertThatThrownBy(() -> simulator().paymentExceptionPort("https://pay.bank/", token)).hasMessageContaining("loopback");
        assertThat(simulator().paymentExceptionPort("http://127.0.0.1:8099/", token)).isNotNull();
    }
}
