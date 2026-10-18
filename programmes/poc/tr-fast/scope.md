# POC TR FAST: scope

The Türkiye FAST proof of concept: does the FAST exception path (TR-04, P2) work end to end on dev-tr? It
starts after POT TR FAST round 1 exits and uses the POT module set plus the providers below. Process and
scenario ids are working names from the phase 5 design, as in `programmes/pot/tr-fast/scope.md`.

| Provider | In the POC as | What it needs (this repo's view) | Read on 2026-10-18 |
|---|---|---|---|
| `acc` accounts ledger | its container image (no code import) | `captureHold`, `releaseHold`: `requested` in `contracts/expectations/acc.yaml` | `api/openapi/accounts-ledger.yaml` at `951a1da` (ledger PR #1 head) has no `/holds` path; default branch `a4e0732` holds only a README |
| `pay` | a stub from `contracts/expectations/pay.yaml` | `Payment.ExceptionRaised.v1` and the decision operations: `requested` | `fintechbankx-lendingpayments-payment-orchestration-initiation-settlement` default branch `18f2352` and branch head `a0e08be` contain no `ExceptionRaised` |
| `psg-tr` | the scheme simulator | `getFastInstructionStatus`: `requested` in `contracts/expectations/psg-tr.yaml` | see `docs/packages/tr-fast.md` for the gateway repository |
| `ntf` | a stub from `contracts/expectations/ntf.yaml` | `sendNotification`: `requested` | no provider release recorded here |

A run against the simulator alone is labelled POC-0 and proves nothing about the ledger.

Scenarios: the three P2 outcomes, settled (capture once), rejected (release) and still pending (operations
queue, then supervisor, then hand-back). The operator screens of `bpo-process-api` join in round 2 if they
have landed. A candidate showcase for round 2 is request to pay (TR-16, TR-17 in `docs/packages/tr-fast.md`,
planned), once its model and the gateway exist.

Out: the same list as `programmes/pot/tr-fast/scope.md`, and the real `cus`, `cmp` and `rsk` services.
