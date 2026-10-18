package com.bank.bpo.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The rule proposed for every domain repository (07 section 2.6, 05 F4): production code depends on no
 * Camunda class. A domain service never starts a process, never holds a job worker and never imports the
 * engine client; the orchestrator calls the service, never the other way round, and only the bpo repository
 * carries io.camunda. The file is copied, not imported (no cross-repository imports), and its sha256 is
 * pinned in the receiving repository's required-gates the way the vendored chart guard is.
 *
 * Use it from the repository's existing architecture test, over its production classes:
 * {@code NoCamundaRule.rule().check(production)}.
 */
public final class NoCamundaRule {

    /** Camunda 8 (io.camunda) and Camunda 7 (org.camunda), in any layer of the service. */
    public static final String[] FORBIDDEN_PACKAGES = {"io.camunda..", "org.camunda.."};

    private NoCamundaRule() {
    }

    public static ArchRule rule() {
        return noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_PACKAGES)
            .as("production code does not depend on the Camunda engine or its client (ADR: orchestration lives in bpo)")
            .because("Camunda holds no money, no business rules and no personal data, and a domain service must run without it");
    }
}
