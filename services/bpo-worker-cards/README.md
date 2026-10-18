# bpo-worker-cards

Job workers for the **cards** family (package `com.bank.bpo.cards`, Keycloak client `svc-bpo-worker-cards`).

Planned processes: the Türkiye card and BKM flows P24-P27, including P25 TROY chargeback (owner: Payment Schemes).

The module is added to `settings.gradle` with its first model, following `bpo-worker-onboarding`:
one port per provider operation, one adapter per port over `ProviderHttp`, one handler per job type,
and a `JobTypeContractTest` that ties the served job types to `models/dictionary/job-types.yaml`.
