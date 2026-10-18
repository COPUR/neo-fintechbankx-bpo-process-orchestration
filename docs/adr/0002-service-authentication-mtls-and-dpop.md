# 0002. Service authentication: mesh mTLS for internal calls, DPoP for user calls

Status: Proposed (local). Owner decision 2026-10-18: internal calls (API calls, SMTP, event listeners, Kafka)
use mesh mTLS; calls a user makes through a UI use DPoP. Needs security-compliance-architect acceptance
(DevSecOps B-5) and the mesh and identity entries below; the programme ADR number is Governance's to assign.

## Context

Job workers call cus, cmp, acc, psg-tr and ntf with a Keycloak client-credentials bearer token
(`svc-bpo-worker-onboarding`). A bearer alone is not sender-constrained: a token taken from a worker pod
replays against every provider until it expires.

What the programme already has (read for this ADR, platform-architect input 2026-10-18):

- ADR-005 (adr-runbooks, Istio service mesh) and ADR-006 (zero trust) put STRICT mTLS on service-to-service
  traffic. Their "Implemented" banners are claims, not evidence.
- No ADR decides DPoP versus mTLS for internal callers. "TPPs DPoP-only" is an open-finance rule for third
  parties. The accounts ledger, the one extracted callee with a service-caller design, documents DPoP as
  not enforced for internal and first-party callers and accepts a bearer whose `aud` contains its own
  audience and whose `azp` is on a configured caller allow-list.
- Mesh objects live in the service-mesh repo under `k8s/istio/security/`: `<svc>-mtls` PeerAuthentication
  STRICT with an ISTIO_MUTUAL DestinationRule, `<svc>-rbac` AuthorizationPolicy by SPIFFE principal
  `cluster.local/ns/<ns>/sa/<sa>`, and `<svc>-network-policy` with a default deny. Charts ship none of them.
- Providers create an `x-fapi-interaction-id` when the caller sends none and echo it back.

## Decision

The owner's split, applied to this repository:

| Call | Who calls | Sender constraint | Authorization |
|---|---|---|---|
| Worker to domain service (cus, cmp, acc, psg-tr, ntf) | `svc-bpo-worker-<family>` | mesh mTLS, STRICT on both sides | bearer `aud` = provider audience, `azp` on the provider's caller list, one scope per provider; AuthorizationPolicy admits the worker principal only on its expectation paths |
| Worker to the engine (job activation and completion) | same | mesh mTLS | engine authorization: own job types only |
| Event bridge from Kafka, and to the engine (message publish) | `svc-bpo-event-bridge` | Kafka over mTLS (SSL listener, platform rule), mesh mTLS to the engine | the only client allowed to publish messages; topic ACLs in the Kafka repo |
| Outbound mail or other channel delivery | not bpo: `ntf` sends, bpo only asks ntf | mesh mTLS to ntf; SMTP from ntf over mTLS/TLS is ntf's concern | as the worker row |
| A user starting a process or completing a task through a UI (`bpo-process-api`, later) | the user's channel app | **DPoP**: the user's access token is DPoP-bound; the API checks the proof on every request, as for TPP APIs | user roles and four-eyes rules in `bpo-process-api` |
| Staff in Operate, Tasklist or Admin (Camunda's own web apps) | staff browser | OIDC through Keycloak behind the ingress gateway, mesh mTLS inside | Camunda authorizations per group. Camunda's web apps do not take DPoP proofs (inference from the chart and product docs); staff task completion for production goes through `bpo-process-api` with DPoP once it exists, and direct Tasklist use stays a staff-network tool |

Rules that follow:

1. A stolen worker bearer is useless outside the worker's workload identity, because the mesh admits only that
   principal. DPoP for internal callers is not required; a superseding ADR could add it to `ProviderHttp`
   and each provider's spec and validator together.
2. Workers use https only, refuse plain http and a fixed token outside the in-process simulator, and send one
   `x-fapi-interaction-id` per business key (derived from it) so one application can be followed across
   providers, steps and retries. W3C `traceparent` comes with the observability wiring of the worker chart.
3. `bpo-process-api` publishes its OpenAPI with `Authorization` + `DPoP` + `x-fapi-interaction-id` on every
   operation, so the platform's FAPI/DPoP guard applies to it unchanged.

## Entries this needs elsewhere (owners)

| Where | Entry | Owner |
|---|---|---|
| service-mesh `k8s/istio/security/` | `bpo-default-mtls` (STRICT), `bpo-deny-all-default`, `bpo-default-deny-all`, `allow-dns-access` and `allow-istio-system` for namespace `bpo` | Mesh Security |
| same | `camunda-orchestration-network-policy`: gRPC 26500 and REST 8080 from bpo worker pods only, Operate and Tasklist through the ingress gateway, broker ports 26501-26502 between broker pods, metrics from observability | Mesh Security |
| same | `bpo-worker-onboarding-network-policy`: egress to the orchestration gateway, Keycloak and the provider namespaces only | Mesh Security |
| same, each provider namespace | `<svc>-rbac` rule admitting `cluster.local/ns/bpo/sa/bpo-worker-onboarding` on its expectation paths | Mesh Security with each provider owner |
| identity realm | `svc-bpo-worker-onboarding` with one scope per provider audience; `azp` added to each provider's caller list | Identity, with each provider owner |

Port numbers are Camunda defaults and are confirmed when the worker chart is written.

## Consequences

- B-5 is closed by acceptance of this ADR plus the mesh and identity entries; the code side (https only,
  interaction id) is in this repository.
- The open question of Camunda's own web apps and DPoP is listed for security-compliance-architect.
- Until the entries exist, the workers run only against the simulator; no provider admits them.
- The mesh repo still uses legacy names (namespace `banking`, selector key `name:`), so the `bpo` entries
  follow the newer namespace naming the extracted charts use; aligning the two is Mesh Security's call.
