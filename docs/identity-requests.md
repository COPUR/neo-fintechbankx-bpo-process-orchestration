# Identity requests

The realm is defined in the identity repository (realm as code). This file lists what this repo needs from
it; the identity owner adds the clients there. Authentication of worker calls is decided in
`docs/adr/0002-service-authentication-mtls-and-dpop.md`.

| Client | Type | Used by | Needs |
|---|---|---|---|
| `camunda-orchestration` | confidential, authorization code + client credentials | the orchestration cluster (Operate, Tasklist, Admin, REST/gRPC) | audience `camunda-orchestration-api`; secret in `camunda-orchestration-oidc/client-secret` |
| `svc-bpo-worker-onboarding` | service, client credentials | onboarding job workers | engine: activate and complete jobs of the onboarding job types only, no process start, no message publish. Providers: one scope per audience (below); its `azp` on each provider's caller allow-list |
| `svc-bpo-worker-payments` | service, client credentials | payments job workers (TR cluster first) | engine: activate and complete jobs of the payments job types only, no process start, no message publish. Providers: one scope per audience (below); its `azp` on each provider's caller allow-list |
| `svc-bpo-event-bridge` | service, client credentials | the event bridge (later) | engine: message publish only. It is the only client allowed to publish `Msg_KycCompleted`, `Msg_SchemeStatusReported` and every later message |
| `svc-bpo-ci-deployer` | service, client credentials | model deployment from `main` (later) | engine: deployment only |
| `svc-bpo-process` | service, client credentials | `bpo-process-api` | engine: user task read, assign, unassign and complete on `lib-screening-review` and `pay-instant-exception` (process start later); also the audience staff tokens carry |
| `bpo-staff-web` | public, authorization code with PKCE (S256), DPoP-bound tokens required | the staff web (`ui/bpo-staff-web`) | scope that adds `svc-bpo-process` to `aud`; `preferred_username` and a `groups` claim with the queue groups below; redirect URI of the staff web host |

Provider audiences for `svc-bpo-worker-onboarding` (the existing client ids, so no new audience is created):

| Provider | Audience | Operations (contracts/expectations) | State |
|---|---|---|---|
| cus | `svc-cus-profile-kyc` | getKycStatus | provider must add the caller |
| cmp | `svc-cmp-evidence` | screenOnboardingApplicant, openReviewCase, recordReviewDecision | provider must add the caller |
| acc | `svc-acc-accounts-ledger` | openApplicationAccount, rejectAccountApplication | caller allow-list (`azp`) entry needed |
| psg-tr | none yet | offerKolayAdresAlias | no client exists; created with the gateway |
| ntf | none yet | sendNotification | no client exists; created with the service |

Separation of duties for staff (requested): no account holds a maker queue group and its checker queue group
of the same process at once (`cmp-*-onboarding-l1` or `cmp-review-l1` with `cmp-*-mlro`,
`cmp-tr-compliance-officer` or `cmp-review-l2`; `pay-tr-fast-ops` or `pay-ops-l1` with `pay-tr-fast-supervisor`
or `pay-ops-l2`), and `preferred_username` cannot be changed by the user. `bpo-process-api` already refuses a
checker decision by anyone who held the maker task; this rule stops the same person reaching both queues.

Groups for user tasks, from `cmp-review-routing`: `cmp-ae-onboarding-l1`, `cmp-ae-mlro`,
`cmp-tr-onboarding-l1`, `cmp-tr-compliance-officer`, `cmp-review-l1`, `cmp-review-l2`.
The engine reads groups from the token; the claim name is set when the realm adds the mapper.

Caller allow-list entries the providers add (`azp` values). Only the onboarding worker calls providers;
`camunda-orchestration` is the engine's own client and calls no provider, so it is on no allow-list.

| Provider | Audience the token carries | `azp` to allow |
|---|---|---|
| cus | `svc-cus-profile-kyc` | `svc-bpo-worker-onboarding` |
| cmp | `svc-cmp-evidence` | `svc-bpo-worker-onboarding` |
| acc | `svc-acc-accounts-ledger` | `svc-bpo-worker-onboarding` |

Provider audiences for `svc-bpo-worker-payments` (P2, `docs/packages/tr-fast.md`):

| Provider | Audience | Operations (contracts/expectations) | `azp` to allow |
|---|---|---|---|
| pay | `svc-pay-initiation-settlement` | recordExceptionDecision, markExceptionSettled, markExceptionRejected | `svc-bpo-worker-payments` |
| acc | `svc-acc-accounts-ledger` | captureHold, releaseHold | `svc-bpo-worker-payments` |
| psg-tr | none yet (`svc-psg-tr` with the gateway) | getFastInstructionStatus | `svc-bpo-worker-payments` |
| ntf | none yet | sendNotification | `svc-bpo-worker-payments` |

Groups for user tasks, from `pay-exception-routing`: `pay-tr-fast-ops`, `pay-tr-fast-supervisor`, `pay-ops-l1`,
`pay-ops-l2`.
