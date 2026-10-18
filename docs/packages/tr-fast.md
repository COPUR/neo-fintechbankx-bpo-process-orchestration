# TR FAST process package

The Türkiye FAST package of this repo: which FAST processes Camunda runs, which stay in the domain services,
and what each one needs from its providers. Every model here is meant for the TR cluster only and is served by
`bpo-worker-payments` running there; where that cluster is hosted is still open (design decision C8-K4, which
proposes Türkiye after a legal opinion).

**Sources and what they prove.** The numbers 02 to 08 refer to the phase 5 design documents: 02 domain split
plan, 03 process and decision inventory, 04 CBDC and BKM process analysis, 05 Camunda 8 solution architecture,
07 Camunda project plan, 08 Türkiye BKM package. They are not published in any repository yet; Governance is to
record them (go-live condition G-P4 below). Until then a section reference here says where a design idea came
from, not that anything was checked. Every TCMB, BKM or regulatory statement in this file is an assumption
taken from that design, and so are the decision ids (C8-K4, F5-K3) and gap ids (GTR-nn) it uses.

## What runs where

FAST is a seconds-budget, 24/7 rail. Its happy path never runs in Camunda (04 short answer, point 3; 05 drivers, point 4): outgoing
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

The FAST rail adapter itself is not in this repo. The design (02 §2.4) puts every rail family in its own gateway
repository, for Türkiye `neo-fintechbankx-paymentschemes-tr-fast-bkm-gateway-service` (`psg-tr`, client
`svc-psg-tr`, events `evt.psg.tr`). On 2026-10-18 no repository with `paymentschemes` in its name was among the
repositories this programme can reach; creating it is the repository owner's step. Until it exists,
`simulators/domain-simulator` stands in for the operations listed in `contracts/expectations/psg-tr.yaml`.

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

## Hypotheses and success metrics

Each process is a bet that Camunda closes a case class faster or safer than people working from the payments
operations view. Every baseline is unknown: no provider operation exists yet, so nothing has been measured. The
first month after a process goes live sets the baseline; the target is then agreed with the operations owner.

| Process | Hypothesis | Success metric | Baseline |
|---|---|---|---|
| P2 `pay-instant-exception` | Most unanswered FAST payments resolve from the scheme's own record, without a person | Share of `Payment.ExceptionRaised` cases on FAST closed SETTLE or RELEASE by the status inquiry, with no user task, within 10 minutes | unknown |
| P6 `pay-return-recall` (TR) | A tracked return request with deadlines gets more misdirected credits back than email and phone | Share of return requests answered by the receiving bank before the deadline; median days to money back | unknown |
| `tr-siper-fraud-case` | A case with SLAs and four-eyes on blocks shortens the time a fraud-related amount stays blocked without a decision | Median hours from SİPER notification to block decision; share of blocks released or confirmed inside the SLA | unknown |
| P8 `pay-safe-payment-escrow` | Timers on held funds stop escrow amounts from staying held past their release or refund date | Number of escrow holds still open after their deadline; share released or refunded automatically | unknown |
| P7 `rtp-lifecycle` (TR) | A request-to-pay that expires or is answered in parts closes cleanly instead of staying open | Share of requests ending in a terminal state (paid, partly paid, declined, expired) with no manual close | unknown |
| `tr-scheme-incident` | A guided incident flow sends customer and regulator notices on time during a FAST or BKM outage | Minutes from monitoring alarm to the first required notice; share of incidents with a closing report | unknown |
| P9 `liq-prefunding-topup` (TR) | A threshold event with an approval step tops up the TCMB account before payments are refused | Number of payments refused for liquidity per month; minutes from threshold event to top-up | unknown |
| P24 `recon-break-resolution` | Routing each FAST end-of-day break to an owner with an SLA clears breaks before the next cycle | Share of breaks closed before the next end of day; age of the oldest open break | unknown |

## Order of the later rounds

Ordered by money at risk and by how much is still unknown, so the riskiest open questions are answered first.
P9 and P24 come last because their owning contexts (`liq`, `reconciliation`) do not exist yet.

| Order | Process | Money at risk | Uncertainty | Why here |
|---|---|---|---|---|
| 1 | P6 return, recall, misdirected credit | High: money already left the bank | High: no FAST return message confirmed (08 §2.1) | Most money exposed and the biggest unknown in the rail's rules |
| 2 | `tr-siper-fraud-case` | High: blocks on customer funds | High: risk flag, block authority and block duration unverified (08 §2.6) | Regulator-facing, needs four-eyes, calls P6 |
| 3 | P8 Güvenli Ödeme escrow | Medium: funds held for days | Medium: registration channel and refund period unverified (08 §2.5) | Builds on the ledger hold already used by P2 |
| 4 | P7 Ödeme İste | Low: a request moves no money | Medium: partial and early payment support unverified (GTR-10) | Customer-visible, but failure only leaves a request open |
| 5 | `tr-scheme-incident` | None directly | Medium: target values unverified (08 §2.1) | Notices and reports, no money step |
| 6 | P9 TCMB liquidity top-up | High | High: mechanism unverified; `liq` does not exist | Blocked on a new context |
| 7 | P24 FAST reconciliation breaks | Medium | High: report format unverified; `reconciliation` does not exist | Blocked on a new context |

## What P2 needs from other repositories

| Provider | Operation or event | Expectation | Owner |
|---|---|---|---|
| `pay` | `EXCEPTION_OWNED_BY_BPO` state and `Payment.ExceptionRaised.v1` on `evt.pay.payment.v1` | `contracts/expectations/pay.yaml` | Payment Orchestration |
| `pay` | `recordExceptionDecision`, `markExceptionSettled`, `markExceptionRejected` | same | Payment Orchestration |
| `acc` | `captureHold`, `releaseHold` on the holds pay places (F5-K3) | `contracts/expectations/acc.yaml` | Core Banking ledger |
| `psg-tr` | `getFastInstructionStatus` and `PaymentSchemes.Instruction.StatusReported.v1` | `contracts/expectations/psg-tr.yaml` | Payment Schemes (no repository on 2026-10-18) |
| `ntf` | `sendNotification` with PAYMENT_SETTLED and PAYMENT_REJECTED | `contracts/expectations/ntf.yaml` | Notification (no repository on 2026-10-18) |
| identity | client `svc-bpo-worker-payments` and its `azp` on each provider | `docs/identity-requests.md` | Platform identity |

All of them are `requested`: none is in the catalog yet, so nothing here runs against a real service.

## Go-live conditions

None of these blocks merging the models; each blocks deploying `bpo-worker-payments` with a real provider URL.

- **G-P1 The capture on SETTLE rests on a rule `pay` has not built.** P2 captures the hold when `pay` answers
  SETTLE, and the only check that a person's SETTLED decision matches the scheme's record is `pay`'s
  (`recordExceptionDecision` in `contracts/expectations/pay.yaml`, status `requested`). The simulator's mapping
  is a test stand-in, not that rule. Until `pay` implements and publishes the check, a human decision alone
  would move money, so the worker is not deployed against a real `pay`.
- **G-P2** `acc` publishes `captureHold` and `releaseHold` with the replay behaviour in
  `contracts/expectations/acc.yaml` (409 on key reuse, 200 on releasing a released hold).
- **G-P3** `docs/adr/0002-service-authentication-mtls-and-dpop.md` applies to this worker family too: the mesh
  and identity entries exist before any provider admits it, only the event bridge publishes messages, and
  Operate and Tasklist stay staff tools. The worker chart, when it is written, refuses the `simulator` profile. Each provider already gets its
  own token scoped to its audience (`bpo.auth.scopes.<provider>`).
- **G-P4** Governance records the design documents and the TCMB and BKM statements this file relies on.

## Values that are assumptions

- The inquiry intervals and the number of reads. The design found no published FAST completion time (08 §2.1,
  marked unverified there); the values are the examples of 05 §6.4 and 07 O4 and move to the certification
  figures when they arrive.
- The operations SLAs (PT30M and PT2H) and the queue names. 05 §12 proposes measuring the first month and then
  fixing them.
- A separate FAST return or recall message: the design confirms the return rule for EFT only, not for FAST
  (08 §2.1, marked unverified). P6 assumes a return is a new
  FAST transfer from the receiving bank referring to the original one.

## Not in this round

- Four-eyes on the decision. The supervisor task is a second queue, not an enforced second person; enforcing
  `assignee != maker` needs `bpo-process-api` (planned).
- The event bridge (start registry, inbox, message publish). The CPT suite starts instances and publishes the
  message the way the bridge will.
- The `psg-tr` gateway, the `liq` and `reconciliation` contexts and the TCMB and BKM memberships (GTR-01,
  GTR-05, GTR-18). The design's exit criteria (08 §11.4) put the TCMB FAST acceptance tests and the BKM
  certifications before production; that is an assumption until Governance records it (G-P4).
