# bpo-worker-engagement

Job workers for the **engagement** family (package `com.bank.bpo.engagement`, Keycloak client `svc-bpo-worker-engagement`).

Planned processes: the campaign scenarios G1-G10 of the POC and POV programmes (owner: Engagement squad). Rewards and budgets are money rules and stay in the `rwd` context; this family only calls it.

The module is added to `settings.gradle` with its first model, following `bpo-worker-onboarding`:
one port per provider operation, one adapter per port over `ProviderHttp`, one handler per job type,
and a `JobTypeContractTest` that ties the served job types to `models/dictionary/job-types.yaml`.
