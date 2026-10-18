# POT TR FAST: scope

The Türkiye FAST proof of technology: does the stack fit our platform for the FAST exception path on the TR
cluster? It runs only the modules FAST needs, so a change to another family does not re-run this evidence.
The package choice follows the component principles (common closure, common reuse, stable dependencies);
see `docs/packages/tr-fast.md` for the processes.

In:
- Models: `models/bpmn/pay/pay-instant-exception.bpmn` (P2), `models/bpmn/_lib/lib-await-external-status.bpmn`
  (L3), `models/dmn/psg/psg-status-inquiry-policy.dmn`, `models/dmn/pay/pay-exception-routing.dmn`.
- Modules: `bpo-worker-payments`, `bpo-worker-kit`, `domain-simulator` (stands in for `psg-tr`, `pay`, `ntf`
  and the `acc` holds, none of which exists yet), `cpt` filtered to `PayInstantExceptionTest`, `lint`.
- Deployment: `deploy/camunda-platform/values-common.yaml`, `values-dev.yaml`, `values-prod-tr.yaml`,
  `chart-lock.yaml`.
- Platform requests: identity client `svc-bpo-worker-payments`, strict mTLS, and in round 2 the topics
  `evt.pay.payment.v1` and `evt.psg.tr` through the event bridge.
- Technical scenario: many concurrent P2 instances, each waiting on up to three growing timers, as the
  FAST-specific timer load.

Out, and why:
- `bpo-worker-onboarding`, P1, `lib-screening-review`, `cmp-review-routing`: FAST never calls them.
- `cus`, `cmp`, `rsk`: the FAST pre-checks stay in `pay`'s synchronous happy path, outside Camunda.
- `values-prod-ae.yaml` and every UAE rail, digital currency wallets, rewards: not Türkiye FAST.
- G5 zero-fee window and P25 TROY chargeback (the general POT scenarios): G5 needs `liq` and a fee rule
  that do not exist; P25 is cards, not FAST. They stay in the general POT.
- The monolith: no FAST step lives there.
