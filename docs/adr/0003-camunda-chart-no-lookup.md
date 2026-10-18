# 0003. Camunda chart: no `lookup`, TLS autoRollout off

Status: Proposed (local). Picked 2026-10-18 as the recommended option after the owner asked for the open items to be settled; the owner can override it. Keep the platform rule "no
`lookup`" and switch off the chart features that need it. Needs Platform acceptance for the cicd render
guard.

## Context

The platform deploy runs with `HELM_DRIVER=configmap` and a deploy Role that has no access to Secrets. A
Helm `lookup` of a Secret under that Role fails `helm upgrade`, and the cicd render guard refuses any
template that calls `lookup`.

camunda-platform 15.1.1 calls `lookup "v1" "Secret"` in four helpers in `templates/common/_helpers.tpl`:
the checksum annotations for the CA bundle and for the orchestration, connectors and optimize certificates.
Each helper starts with `if ... autoRollout`, and every `autoRollout` defaults to false. The `common`
subchart's `common.secrets.lookup` helper is defined but no template of the chart includes it.

## Options

1. Keep the rule and pin every `autoRollout` to false (chosen).
2. Give the deploy Role `get` on Secrets in the `bpo` namespace so the flags can be on.
3. Fork the chart and delete the helpers.

## Decision

Option 1. `values-common.yaml` pins `global.tls.{caBundle,orchestration,connectors,optimize}.autoRollout`
to false, and `scripts/ci/camunda-chart-render.sh` refuses a values file that turns one on or drops the
pin. Camunda's own TLS stays off: transport security between pods is mesh mTLS (ADR 0002).

## Consequences

- The deploy identity keeps no Secret access, and the chart keeps its upstream form.
- The template text still contains the word `lookup`, so the line-based cicd guard needs an allowance
  for this vendored chart, conditioned on the render script above passing. That allowance is Platform's
  change.
- If chart TLS is ever enabled, a certificate rotation needs `kubectl rollout restart`; the pods do not
  roll on their own.
