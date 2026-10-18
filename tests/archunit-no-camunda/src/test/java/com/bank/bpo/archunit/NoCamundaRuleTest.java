package com.bank.bpo.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Red and green for the rule: a clean package passes, and each way of reaching the engine is refused. */
class NoCamundaRuleTest {

    private static JavaClasses classesIn(String pkg) {
        return new ClassFileImporter().importPackages(pkg);
    }

    @Test
    void aClassWithoutEngineDependenciesPasses() {
        JavaClasses clean = classesIn("fixture.clean");
        assertThat(clean).isNotEmpty();
        assertThatCode(() -> NoCamundaRule.rule().check(clean)).doesNotThrowAnyException();
    }

    @Test
    void anEngineClientFieldIsRefused() {
        assertThatThrownBy(() -> NoCamundaRule.rule().check(classesIn("fixture.violating")))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("EngineClientField")
            .hasMessageContaining("io.camunda.client.CamundaClient");
    }

    @Test
    void aJobWorkerAnnotationIsRefused() {
        assertThatThrownBy(() -> NoCamundaRule.rule().check(classesIn("fixture.violating")))
            .hasMessageContaining("JobWorkerAnnotation")
            .hasMessageContaining("io.camunda.client.annotation.JobWorker");
    }

    @Test
    void aThrownEngineTypeIsRefused() {
        assertThatThrownBy(() -> NoCamundaRule.rule().check(classesIn("fixture.violating")))
            .hasMessageContaining("ThrowsEngineError")
            .hasMessageContaining("io.camunda.client.exception.CamundaError");
    }

    @Test
    void theBpoWorkersThemselvesAreWhatTheRuleKeepsOutOfDomainRepositories() {
        // The same rule over the onboarding worker is red: that code belongs in this repository only.
        assertThatThrownBy(() -> NoCamundaRule.rule().check(classesIn("com.bank.bpo.onboarding.handler")))
            .hasMessageContaining("io.camunda");
    }
}
