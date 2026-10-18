# Proposal: keep io.camunda out of the domain repositories

Status: proposed to Governance (it becomes a guardrail only through an ADR there).

## Why

Orchestration calls domain services; a domain service never calls the engine. If a domain repo
imports the Camunda client it can start processes, hold job workers or throw BPMN errors, and the
engine becomes part of its runtime and of its tests. The rule keeps the dependency one-way and
keeps every domain service runnable without Camunda.

## The rule

`tests/archunit-no-camunda/src/main/java/com/bank/bpo/archunit/NoCamundaRule.java`:

```java
noClasses()
    .should().dependOnClassesThat().resideInAnyPackage("io.camunda..", "org.camunda..")
```

Its test in this repo proves both directions: a plain service class passes; a class with an engine
client field, a `@JobWorker` method, or a thrown `BpmnError` is refused; and the rule is red over
this repo's own onboarding workers, which is exactly the code it exists to keep out.

## How a domain repo adopts it

No cross-repo imports, so the rule is copied, the same way the platform chart guard is vendored:

1. Copy the rule body into the repo's existing ArchUnit test (it becomes the sixth rule next to the
   five hexagonal ones), over production classes only:

   ```java
   @ArchTest
   static final ArchRule no_camunda = noClasses()
       .should().dependOnClassesThat().resideInAnyPackage("io.camunda..", "org.camunda..")
       .because("orchestration lives in bpo; a domain service runs without the engine");
   ```

2. Refuse the dependency itself at build time, so it fails before any class is written:

   ```groovy
   configurations.configureEach {
       resolutionStrategy.eachDependency { details ->
           if (details.requested.group.startsWith('io.camunda') || details.requested.group.startsWith('org.camunda')) {
               throw new GradleException("${details.requested} is not allowed in a domain repository (orchestration lives in bpo)")
           }
       }
   }
   ```

3. Waivers: none expected. A repo that needs one asks Governance for an ADR.

## Rollout

Governance owns the guardrail text. Each owning thread adds the rule in its next round, with the
red-then-green evidence its CI already requires; the monolith is out of scope (it is retiring).
