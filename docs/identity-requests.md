# Identity requests

The realm is defined in the identity repository (realm as code). This file lists what this repo needs from
it; the identity owner adds the clients there. Authentication of worker calls is decided in
`docs/adr/0002-worker-to-provider-authentication.md`.

| Client | Type | Used by | Needs |
|---|---|---|---|
| `camunda-orchestration` | confidential, authorization code + client credentials | the orchestration cluster (Operate, Tasklist, Admin, REST/gRPC) | audience `camunda-orchestration-api`; secret in `camunda-orchestration-oidc/client-secret` |
| `svc-bpo-worker-onboarding` | service, client credentials | onboarding job workers | engine: activate and complete jobs of the onboarding job types only, no process start, no message publish. Providers: one scope per audience (below); its `azp` on each provider's caller allow-list |
| `svc-bpo-event-bridge` | service, client credentials | the event bridge (later) | engine: message publish only. It is the only client allowed to publish `Msg_KycCompleted` and every later message |
| `svc-bpo-ci-deployer` | service, client credentials | model deployment from `main` (later) | engine: deployment only |
| `svc-bpo-process` | service | `bpo-process-api` (later) | engine: process start and user task permissions |

Provider audiences for `svc-bpo-worker-onboarding` (the existing client ids, so no new audience is created):

| Provider | Audience | Operations (contracts/expectations) | State |
|---|---|---|---|
| cus | `svc-cus-profile-kyc` | getKycStatus | provider must add the caller |
| cmp | `svc-cmp-evidence` | screenOnboardingApplicant, openReviewCase, recordReviewDecision | provider must add the caller |
| acc | `svc-acc-accounts-ledger` | openApplicationAccount, rejectAccountApplication | caller allow-list (`azp`) entry needed |
| psg-tr | none yet | offerKolayAdresAlias | no client exists; created with the gateway |
| ntf | none yet | sendNotification | no client exists; created with the service |

Groups for user tasks, from `cmp-review-routing`: `cmp-ae-onboarding-l1`, `cmp-ae-mlro`,
`cmp-tr-onboarding-l1`, `cmp-tr-compliance-officer`, `cmp-review-l1`, `cmp-review-l2`.
The engine reads groups from the token; the claim name is set when the realm adds the mapper.
