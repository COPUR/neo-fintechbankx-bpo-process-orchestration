package com.bank.bpo.processapi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Ports and adapters: controllers and the engine adapter meet only through the application and its port. */
class HexagonalArchitectureTest {

    private static JavaClasses production;

    @BeforeAll
    static void importProduction() {
        production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bank.bpo.processapi");
    }

    @Test
    void theDomainAndTheApplicationKnowNoFrameworkHttpOrJson() {
        noClasses().that().resideInAnyPackage("..processapi.domain..", "..processapi.application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "java.net..", "tools.jackson..",
                "io.camunda..", "..processapi.adapter..", "..processapi.config..")
            .check(production);
    }

    @Test
    void controllersDoNotReachTheEngineAdapter() {
        noClasses().that().resideInAPackage("..processapi.adapter.web..")
            .should().dependOnClassesThat().resideInAnyPackage("..processapi.adapter.camunda..", "java.net.http..")
            .check(production);
    }

    @Test
    void theEngineAdapterDoesNotKnowTheWeb() {
        noClasses().that().resideInAPackage("..processapi.adapter.camunda..")
            .should().dependOnClassesThat().resideInAnyPackage("..processapi.adapter.web..", "org.springframework..")
            .check(production);
    }

    @Test
    void nothingLoadsTheCamundaClient() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("io.camunda..").check(production);
    }
}
