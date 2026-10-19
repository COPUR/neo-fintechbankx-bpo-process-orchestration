package com.bank.bpo.workerkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DecisionReasonsTest {

    @Test
    void aPersonsDecisionCarriesAHumanCode() {
        assertThat(DecisionReasons.require("SETTLED", "EXPIRED", "OTHER")).isEqualTo("OTHER");
    }

    @Test
    void theExpiryCarriesTheSystemCode() {
        assertThat(DecisionReasons.require("EXPIRED", "EXPIRED", "SLA_EXPIRED")).isEqualTo("SLA_EXPIRED");
    }

    @Test
    void aPersonCannotClaimTheSystemCodeAndTheExpiryCannotCarryAHumanOne() {
        assertContractFault(() -> DecisionReasons.require("SETTLED", "EXPIRED", "SLA_EXPIRED"));
        assertContractFault(() -> DecisionReasons.require("EXPIRED", "EXPIRED", "OTHER"));
    }

    @Test
    void freeTextOrAMissingReasonIsRefused() {
        for (Object bad : new Object[] {null, "", "customer called", "other", 7}) {
            assertContractFault(() -> DecisionReasons.require("REJECTED", "EXPIRED", bad));
        }
    }

    private static void assertContractFault(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ProviderException.class,
            (e) -> assertThat(e.kind()).isEqualTo(ProviderException.Kind.CONTRACT));
    }
}
