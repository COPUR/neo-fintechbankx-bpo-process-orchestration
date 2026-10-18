# bpo-worker-payments

Job workers for the **payments** family (package `com.bank.bpo.payments`, Keycloak client `svc-bpo-worker-payments`).

Planned processes: P2 instant payment exceptions, P5 bulk file execution, P6 return and recall, P7 request to pay, P8 safe payment escrow (owners: Payment Orchestration; Recurring and Bulk for P5/P7).

The module is added to `settings.gradle` with its first model, following `bpo-worker-onboarding`:
one port per provider operation, one adapter per port over `ProviderHttp`, one handler per job type,
and a `JobTypeContractTest` that ties the served job types to `models/dictionary/job-types.yaml`.
