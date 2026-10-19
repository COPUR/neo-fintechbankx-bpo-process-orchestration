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
| `bpo-staff-web` | public, authorization code with PKCE (S256), DPoP-bound tokens required | the staff web (`ui/bpo-staff-web`) | scope that adds `svc-bpo-process` to `aud`; `sub`, `preferred_username` and a `groups` claim with full group paths (queues as `/staff/fbx-<queue>`); redirect URI of the staff web host |

Provider audiences for `svc-bpo-worker-onboarding` (the existing client ids, so no new audience is created):

| Provider | Audience | Operations (contracts/expectations) | State |
|---|---|---|---|
| cus | `svc-cus-profile-kyc` | getKycStatus | provider must add the caller |
| cmp | `svc-cmp-evidence` | screenOnboardingApplicant, openReviewCase, recordReviewDecision | provider must add the caller |
| acc | `svc-acc-accounts-ledger` | openApplicationAccount, rejectAccountApplication | caller allow-list (`azp`) entry needed |
| psg-tr | none yet | offerKolayAdresAlias | no client exists; created with the gateway |
| ntf | none yet | sendNotification | no client exists; created with the service |

Queue groups for staff: each queue `<queue>` below is a directory group `cn=fbx-<queue>` that the token carries
as `/staff/fbx-<queue>` (the ten queue groups and the staff browser flow: repository `fintechbankx-platform-identity-iam-keycloak-ldap` at `38b6d73`, `docs/identity/REALM_AS_CODE.md`, sections "Groups" and
"Who signs in to staff web") (`bpo-process-api` drops the `/staff/fbx-` prefix and ignores every other group). Queue
users also hold a staff role group and sign in through the realm's staff browser flow. The engine assignee is
the token's `sub`, which never changes; `preferred_username` follows the directory and is shown. Tasks claimed
before the assignee was `sub` carry the user name, so four eyes also refuses a user whose current
`preferred_username` is the maker task's assignee; that check only refuses, never grants.

Separation of duties for staff (requested): no account holds a maker queue group and its checker queue group
of the same process at once (`fbx-cmp-*-onboarding-l1` or `fbx-cmp-review-l1` with `fbx-cmp-*-mlro`,
`fbx-cmp-tr-compliance-officer` or `fbx-cmp-review-l2`; `fbx-pay-tr-fast-ops` or `fbx-pay-ops-l1` with
`fbx-pay-tr-fast-supervisor` or `fbx-pay-ops-l2`). The realm checks this at sign-in and reset only (same file, "Separation
of duties for bpo staff queues" and gap 5: a refresh runs no flow), so the API keeps its own refusal. `bpo-process-api` refuses the checker
task to whoever is the maker task's last recorded assignee, but the engine keeps no earlier assignees, so someone
who released the maker task before it ended is only stopped by this rule.

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
| pay | `svc-pay-initiation-settlement` | recordExceptionDecision, markExceptionSettled, markExceptionRejected, applyFastReturn, closeRecallCase | `svc-bpo-worker-payments` |
| acc | `svc-acc-accounts-ledger` | captureHold, releaseHold | `svc-bpo-worker-payments` |
| psg-tr | none yet (`svc-psg-tr` with the gateway) | getFastInstructionStatus, sendFastRecallRequest, getFastRecallStatus | `svc-bpo-worker-payments` |
| ntf | none yet | sendNotification | `svc-bpo-worker-payments` |

Groups for user tasks, from `pay-exception-routing`: `pay-tr-fast-ops`, `pay-tr-fast-supervisor`, `pay-ops-l1`,
`pay-ops-l2`.
