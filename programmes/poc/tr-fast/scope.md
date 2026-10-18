# POC TR FAST: scope

The Türkiye FAST proof of concept: does the FAST exception path (TR-04, P2) work end to end on dev-tr? It
starts after POT TR FAST round 1 exits and uses the POT module set plus the providers below.

| Provider | In the POC as | Blocking gap |
|---|---|---|
| `acc` accounts ledger | its container image (no code import) | `captureHold` and `releaseHold` (`contracts/expectations/acc.yaml`) are not in the ledger's API yet |
| `pay` | a stub from `contracts/expectations/pay.yaml` | `Payment.ExceptionRaised.v1` and the decision operations are not built |
| `psg-tr` | the scheme simulator | the gateway repository has not been created |
| `ntf` | a stub from `contracts/expectations/ntf.yaml` | the service does not exist |

Until the `acc` holds exist, a run against the simulator alone is labelled POC-0 and proves nothing about
the ledger.

Scenarios: the three P2 outcomes, settled (capture once), rejected (release) and still pending (operations
queue, then supervisor, then hand-back). The operator screens of `bpo-process-api` join in round 2 if they
have landed. G2 "pay from the poster" (FAST-TR Karekod with Ödeme İste, P7) is the first FAST showcase for
round 2, once P7 and `psg-tr` exist.

Out: the same list as `programmes/pot/tr-fast/scope.md`, and the real `cus`, `cmp` and `rsk` services.
