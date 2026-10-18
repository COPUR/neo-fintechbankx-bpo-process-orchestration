# POT round 1: scope

In:
- Repo skeleton, model lint (bpmnlint recommended + camunda-compat `camunda-cloud-8-10` + fbx rules).
- Worker kit and the onboarding workers for P1.
- P1 `acc-tpp-account-opening` and `lib-screening-review` with `cmp-review-routing`, run by Camunda
  Process Test on `camunda/camunda:8.10.4` against the domain simulator.
- Camunda chart 15.1.1 values (dev, prod-ae, prod-tr) rendered and checked.
- The no-Camunda ArchUnit rule proposal for domain repos.

Out (round 2 or later): dev installation, Keycloak clients, mTLS proof, OpenSearch, model deployment,
the event bridge, load and chaos, restore drill.
