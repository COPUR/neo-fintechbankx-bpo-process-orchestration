# Identity requests

The realm is defined in the identity repository (realm as code). This file lists what this repo
needs from it; the identity owner adds the clients there.

| Client | Type | Used by | Needs |
|---|---|---|---|
| `camunda-orchestration` | confidential, authorization code + client credentials | the orchestration cluster (Operate, Tasklist, Admin, REST/gRPC) | audience `camunda-orchestration-api`; secret in `camunda-orchestration-oidc/client-secret` |
| `svc-bpo-worker-onboarding` | service, client credentials | onboarding job workers | engine audience `camunda-orchestration-api` (job activation and completion only); provider audiences `svc-cus-customer-profile`, `svc-cmp-compliance`, `svc-acc-accounts-ledger`, `svc-psg-tr`, `svc-ntf` for the operations in `contracts/expectations/` |
| `svc-bpo-ci-deployer` | service, client credentials | model deployment from `main` (later) | engine audience, deployment permission only |
| `svc-bpo-process` | service | `bpo-process-api` (later) | engine audience; process start and user task permissions |

Groups for user tasks, from `cmp-review-routing`: `cmp-ae-onboarding-l1`, `cmp-ae-mlro`,
`cmp-tr-onboarding-l1`, `cmp-tr-compliance-officer`, `cmp-review-l1`, `cmp-review-l2`.
The engine reads groups from the token; the claim name is set when the realm adds the mapper.
