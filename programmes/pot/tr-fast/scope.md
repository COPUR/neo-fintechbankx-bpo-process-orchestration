# POT TR FAST: scope

The Türkiye FAST proof of technology: does the stack fit our platform for the FAST exception path on the TR
cluster? It runs only the modules FAST needs, so a change to another family does not re-run this evidence.
The package choice follows the component principles (common closure, common reuse, stable dependencies);
see `docs/packages/tr-fast.md` for the processes.

Process and scenario ids (TR-04, P2, L3, G5, P25) come from the phase 5 design, which is not in this
repository; like the ids in `docs/packages/tr-fast.md`, they are working names, not published records.

In:
- Models: `models/bpmn/pay/pay-instant-exception.bpmn` (P2), `models/bpmn/_lib/lib-await-external-status.bpmn`
  (L3), `models/dmn/psg/psg-status-inquiry-policy.dmn`, `models/dmn/pay/pay-exception-routing.dmn`.
- Modules: `bpo-worker-payments`, `bpo-worker-kit`, `domain-simulator`, `cpt` filtered to
  `PayInstantExceptionTest`, `lint`. The simulator stands in for every provider operation P2 calls: this
  repo's `contracts/expectations/{psg-tr,pay,ntf,acc}.yaml` mark those operations `requested`, and no provider
  release is recorded here.
- Deployment: `deploy/camunda-platform/values-common.yaml`, `values-dev.yaml`, `values-prod-tr.yaml`,
  `chart-lock.yaml`.
- Platform requests: identity client `svc-bpo-worker-payments` (`docs/identity-requests.md`), strict mTLS,
  and in round 2 the topics `evt.pay.payment.v1` and `evt.psg.tr` through the event bridge.
- Technical scenario: many concurrent P2 instances, each waiting on up to three growing timers, as the
  FAST-specific timer load.

Out, and why:
- `bpo-worker-onboarding`, P1, `lib-screening-review`, `cmp-review-routing`: the P2 model never calls them.
- `cus`, `cmp`, `rsk`: P2 has no job type for them; the FAST pre-checks belong to `pay`'s synchronous path
  (`docs/packages/tr-fast.md`, "What runs where").
- `values-prod-ae.yaml` and every UAE rail, digital currency wallets, rewards: not Türkiye FAST.
- The general POT scenarios G5 (zero-fee window) and P25 (card chargeback): this repo has no model, DMN or
  contract expectation for either (`services/bpo-worker-cards/README.md` lists P25 as planned), and P25 is a
  card process, not FAST. They stay in the general POT.
