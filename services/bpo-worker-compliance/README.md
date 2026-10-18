# bpo-worker-compliance

Job workers for the **compliance** family (package `com.bank.bpo.compliance`, Keycloak client `svc-bpo-worker-compliance`).

Planned processes: P3 sanctions investigation and P13 regulatory report cycle (owner: Risk and Compliance Decisioning). The shared review sub-process `lib-screening-review` is served by `bpo-worker-onboarding` until this module exists.

The module is added to `settings.gradle` with its first model, following `bpo-worker-onboarding`:
one port per provider operation, one adapter per port over `ProviderHttp`, one handler per job type,
and a `JobTypeContractTest` that ties the served job types to `models/dictionary/job-types.yaml`.
