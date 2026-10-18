# bpo-worker-lending

Job workers for the **lending** family (package `com.bank.bpo.lending`, Keycloak client `svc-bpo-worker-lending`).

Planned processes: P4 origination to disbursement, P11 delinquency and collections, P12 murabaha (owners: Loan Lifecycle, Islamic Finance).

The module is added to `settings.gradle` with its first model, following `bpo-worker-onboarding`:
one port per provider operation, one adapter per port over `ProviderHttp`, one handler per job type,
and a `JobTypeContractTest` that ties the served job types to `models/dictionary/job-types.yaml`.
