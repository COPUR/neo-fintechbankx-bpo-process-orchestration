package com.bank.bpo.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** DevSecOps B-1: no fixed token and no plain http outside the simulator, and the simulator stays on loopback. */
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
        assertThatThrownBy(() -> deployed().workerToken("", "svc-bpo-worker-onboarding", "x", "aud"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("token-uri is required");
    }

    @Test
    void aDeployedWorkerRefusesAPlainHttpTokenEndpoint() {
        assertThatThrownBy(() -> deployed().workerToken("http://keycloak.bank/token", "svc-bpo-worker-onboarding", "x", "aud"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
    }

    @Test
    void aDeployedWorkerNeedsItsCredentialAndAScope() {
        assertThatThrownBy(() -> deployed().workerToken("https://keycloak.bank/token", "svc-bpo-worker-onboarding", "", "aud"))
            .hasMessageContaining("client credential");
        assertThatThrownBy(() -> deployed().workerToken("https://keycloak.bank/token", "svc-bpo-worker-onboarding", "x", ""))
            .hasMessageContaining("scope");
    }

    @Test
    void aDeployedWorkerRefusesAPlainHttpProvider() {
        Supplier<String> token = () -> "t";
        assertThatThrownBy(() -> deployed().accountApplicationPort("http://acc.bank/", token)).hasMessageContaining("https");
        assertThat(deployed().accountApplicationPort("https://acc.bank/", token)).isNotNull();
    }

    @Test
    void theSimulatorTokenOnlyTravelsToLoopback() {
        Supplier<String> token = simulator().workerToken("", "svc-bpo-worker-onboarding", "", "");
        assertThat(token.get()).isEqualTo("simulator-token");
        assertThatThrownBy(() -> simulator().kycPort("http://cus.bank/", token)).hasMessageContaining("loopback");
        assertThatThrownBy(() -> simulator().kycPort("https://cus.bank/", token)).hasMessageContaining("loopback");
        assertThat(simulator().kycPort("http://127.0.0.1:8099/", token)).isNotNull();
    }
}
