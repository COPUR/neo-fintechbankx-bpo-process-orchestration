# 0002. Worker-to-provider authentication: mesh mTLS plus audience and caller checks

Status: Proposed (local). Needs security-compliance-architect acceptance (DevSecOps B-5) and the mesh and
identity entries below; the programme ADR number is Governance's to assign.

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

1. Worker-to-provider calls are sender-constrained by the mesh, not by DPoP: STRICT mTLS on both sides, and
   each provider's AuthorizationPolicy admits the worker's principal
   (`cluster.local/ns/bpo/sa/bpo-worker-<family>`) only on the paths in `contracts/expectations/<provider>.yaml`.
   A stolen bearer is useless from outside that workload identity.
2. The bearer still carries authorization: `aud` is the provider's audience, `azp` is the worker family's
   client, and each provider adds that client to its caller allow-list. One scope per provider audience, so
   the onboarding client cannot call an operation another family owns.
3. Workers use https only, refuse plain http and a fixed token outside the in-process simulator, and send one
   `x-fapi-interaction-id` per business key (derived from it) so one application can be followed across
   providers, steps and retries. W3C `traceparent` comes with the observability wiring of the worker chart.
4. Only the event bridge client may publish messages to the engine; workers may activate and complete jobs
   of their own job types only.
5. DPoP for internal callers stays possible later: a superseding ADR would add it to `ProviderHttp` and to
   each provider's spec and validator at the same time.

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

- B-5 is closed by acceptance of this ADR plus the mesh and identity entries, not by code in this repo.
- Until the entries exist, the workers run only against the simulator; no provider admits them.
- The mesh repo still uses legacy names (namespace `banking`, selector key `name:`), so the `bpo` entries
  follow the newer namespace naming the extracted charts use; aligning the two is Mesh Security's call.
