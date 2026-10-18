# TR FAST process package

The Türkiye FAST package of this repo: which FAST processes Camunda runs, which stay in the domain services,
and what each one needs from its providers. Sources: 03 (process and decision inventory), 04 §3.1, 05 §6 and
§7, 07 §3.2-3.3 and 08 §2 and §10 of the phase 5 design. Every model here deploys only to the TR cluster
(C8-K4) and is served by `bpo-worker-payments` running there.

## What runs where

FAST is a seconds-budget, 24/7 rail. Its happy path never runs in Camunda (04 §1 point 3, 05 §6.1): outgoing
and incoming transfers, Kolay Adres registration and lookup, FAST-TR Karekod, SİPER queries and the rail
choice stay in `pay` and the Türkiye rail gateway (`psg-tr`). Camunda takes the long, multi-party, timed and
human parts.

| TR process (08 §10.1) | Execution | Model in this repo | State |
|---|---|---|---|
| TR-02 outgoing FAST, TR-03 incoming FAST | Synchronous, `pay` + `psg-tr` + `acc` | none | not bpo |
| TR-04 FAST timeout and unknown status | BPMN | `pay-instant-exception` (P2) with `lib-await-external-status` (L3) | **this round** |
| TR-05 FAST return, recall, misdirected credit | BPMN | `pay-return-recall` (P6, TR profile) | next |
| TR-16, TR-17 Ödeme İste (request to pay) | BPMN | `rtp-lifecycle` (P7, TR profile) | planned |
| TR-19 Güvenli Ödeme operator (escrow hold, release, refund) | BPMN | `pay-safe-payment-escrow` (P8) | planned |
| TR-21 SİPER fraud case | BPMN | `tr-siper-fraud-case` (new; number from Governance) | planned |
| TR-06 TCMB account liquidity top-up | BPMN | `liq-prefunding-topup` (P9, TR profile) | planned, needs `liq` |
| TR-07 FAST end-of-day reconciliation breaks | Batch + BPMN | `recon-break-resolution` (P24) | planned, needs `reconciliation` |
| TR-50 FAST scheme outage and incident | BPMN | `tr-scheme-incident` (new) | planned |
| TR-08..TR-13, TR-18, TR-20, TR-25 | Synchronous or DMN in the owning service | none | not bpo |

The FAST rail adapter itself is not in this repo: 02 §3 puts every rail family in its own gateway repository,
for Türkiye `neo-fintechbankx-paymentschemes-tr-fast-bkm-gateway-service` (`psg-tr`, client `svc-psg-tr`,
events `evt.psg.tr`). That repository has not been created; until it is, `simulators/domain-simulator` stands in
for the operations listed in `contracts/expectations/psg-tr.yaml`.

## P2 `pay-instant-exception` (TR-04)

1. `pay` sends the instruction to FAST (the pivot of the happy path). P2 expects `pay`, on a timeout or an
   unknown answer, to set the payment to `EXCEPTION_OWNED_BY_BPO`, stop acting on late answers and emit
   `Payment.ExceptionRaised.v1` (requested in `contracts/expectations/pay.yaml`, not built yet). The event bridge
   starts one instance per `paymentId` with `jurisdiction` and `rail`.
2. `lib-await-external-status` reads `psg-tr`'s record of the instruction. It never sends the instruction again.
   A pending status waits for the gateway's event or for a growing timer (`psg-status-inquiry-policy`: PT30S,
   PT2M, PT10M) and reads again, four reads at most. The event only wakes the instance.
3. ACSC: `acc` captures the hold once (`<paymentId>:capture`, the pivot of P2), `pay` is marked settled with
   the journal reference, the customer is notified.
4. RJCT: `acc` releases the hold (`<paymentId>:release`), `pay` is marked rejected, the customer is notified.
5. Still pending after the last read: `pay-exception-routing` picks the queue (`pay-tr-fast-ops`, then
   `pay-tr-fast-supervisor`). The decision goes to `pay`, which checks it against the gateway's record and
   answers SETTLE, RELEASE or HANDED_BACK. Nobody deciding in time is EXPIRED, which `pay` answers with
   HANDED_BACK: no money moves and the payment leaves bpo's ownership.
6. A hold that already lapsed (410 `HOLD_EXPIRED`), an unknown status, a crafted id or a rail without a
   gateway stops the instance on an incident. Nothing is guessed.

The instance holds `paymentId`, `jurisdiction`, `rail`, `schemeStatus`, `journalRef`, `notificationRef` and the
routing and decision enums. No amount, IBAN, alias, name or ISO 20022 body enters Camunda (05 §5.1); the CPT
suite checks the variable list and the size.

## What P2 needs from other repositories

| Provider | Operation or event | Expectation | Owner |
|---|---|---|---|
| `pay` | `EXCEPTION_OWNED_BY_BPO` state and `Payment.ExceptionRaised.v1` on `evt.pay.payment.v1` | `contracts/expectations/pay.yaml` | Payment Orchestration |
| `pay` | `recordExceptionDecision`, `markExceptionSettled`, `markExceptionRejected` | same | Payment Orchestration |
| `acc` | `captureHold`, `releaseHold` on the holds pay places (F5-K3) | `contracts/expectations/acc.yaml` | Core Banking ledger |
| `psg-tr` | `getFastInstructionStatus` and `PaymentSchemes.Instruction.StatusReported.v1` | `contracts/expectations/psg-tr.yaml` | Payment Schemes (repository to be created) |
| `ntf` | `sendNotification` with PAYMENT_SETTLED and PAYMENT_REJECTED | `contracts/expectations/ntf.yaml` | Notification (service to be created) |
| identity | client `svc-bpo-worker-payments` and its `azp` on each provider | `docs/identity-requests.md` | Platform identity |

All of them are `requested`: none is in the catalog yet, so nothing here runs against a real service.

## Values that are assumptions

- The inquiry intervals and the number of reads. TCMB has not published the FAST completion time (08 §2.1); the
  values are the examples of 05 §6.4 and 07 O4 and move to the certification figures when they arrive.
- The operations SLAs (PT30M and PT2H) and the queue names. 05 §12 proposes measuring the first month and then
  fixing them.
- A separate FAST return or recall message: not confirmed for FAST (08 §2.1). P6 assumes a return is a new
  FAST transfer from the receiving bank referring to the original one.

## Not in this round

- Four-eyes on the decision. The supervisor task is a second queue, not an enforced second person; enforcing
  `assignee != maker` needs `bpo-process-api` (planned).
- The event bridge (start registry, inbox, message publish). The CPT suite starts instances and publishes the
  message the way the bridge will.
- The `psg-tr` gateway, the `liq` and `reconciliation` contexts and the TCMB and BKM memberships (GTR-01,
  GTR-05, GTR-18). Production needs the TCMB FAST acceptance tests and the BKM certifications first (08 §11.4).
