package com.bank.bpo.onboarding;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.camunda.client.annotation.JobWorker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The ports-and-adapters rules of the worker: the ports know nothing of the engine or of HTTP. */
class HexagonalArchitectureTest {

    private static JavaClasses production;

    @BeforeAll
    static void importProduction() {
        production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bank.bpo.onboarding");
    }

    @Test
    void portsDependOnNeitherTheEngineNorHttpNorAdapters() {
        noClasses().that().resideInAPackage("..onboarding.port..")
            .should().dependOnClassesThat().resideInAnyPackage("io.camunda..", "java.net..", "..onboarding.adapter..",
                "..onboarding.handler..", "org.springframework..", "tools.jackson..")
            .check(production);
    }

    @Test
    void handlersTalkToPortsNotToAdapters() {
        noClasses().that().resideInAPackage("..onboarding.handler..")
            .should().dependOnClassesThat().resideInAnyPackage("..onboarding.adapter..", "java.net..")
            .check(production);
    }

    @Test
    void adaptersDoNotKnowTheEngineOrTheHandlers() {
        noClasses().that().resideInAPackage("..onboarding.adapter..")
            .should().dependOnClassesThat().resideInAnyPackage("io.camunda..", "..onboarding.handler..")
            .check(production);
    }

    @Test
    void everyAdapterImplementsAPort() {
        classes().that().resideInAPackage("..onboarding.adapter..")
            .should().implement(com.tngtech.archunit.base.DescribedPredicate.describe("a port interface",
                (i) -> i.getPackageName().endsWith("onboarding.port")))
            .check(production);
    }

    @Test
    void onlyHandlersDeclareJobWorkers() {
        noClasses().that().resideOutsideOfPackage("..onboarding.handler..")
            .should().dependOnClassesThat().areAssignableTo(JobWorker.class)
            .check(production);
    }
}
