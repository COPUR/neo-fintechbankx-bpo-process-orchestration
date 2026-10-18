# 0003. Camunda chart: no `lookup`, TLS autoRollout off

Status: Proposed (local). Picked 2026-10-18 as the recommended option after the owner asked for the open items to be settled; the owner can override it. Keep the platform rule "no
`lookup`" and switch off the chart features that need it. Depends on two Platform PRs that are open, not merged:
fintechbankx-platform-delivery-iac-cicd-templates PR #11 (`HELM_DRIVER=configmap`, the render guard that refuses `lookup`) and
fintechbankx-platform-mesh-security-service-mesh PR #11 (the deploy Role without Secret access). Needs Platform acceptance.

## Context

Proposed, not yet on main: fintechbankx-platform-delivery-iac-cicd-templates PR #11 (`.github/workflows/helm-deploy.yml`) makes the deploy run with
`HELM_DRIVER=configmap` and adds a render guard that refuses any template calling `lookup`; fintechbankx-platform-mesh-security-service-mesh PR #11 gives the deploy the
Role `fintechbankx-deploy` (`deploy/kustomize/base/generated/deploy-rbac.yaml`) with no access to Secrets. Once both merge, a Helm `lookup` of a Secret fails `helm upgrade`. Until
then nothing on main stops it; this ADR keeps the chart ready for those rules.

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
to false, and `scripts/ci/check-camunda-autorollout.sh` (run by the render script, tested by
`scripts/ci/test-camunda-autorollout.sh`) refuses a values file that turns one on, in any case or quoting,
or drops the pin. Camunda's own TLS stays off: transport security between pods is mesh mTLS (ADR 0002).

## Consequences

- If the two PRs merge, the deploy identity needs no Secret access for this chart, and the chart keeps
  its upstream form. If they do not merge, the pins still stand but protect nothing new.
- The template text still contains the word `lookup`, so the line-based cicd guard needs an allowance
  for this vendored chart, conditioned on the render script above passing. That allowance is Platform's
  change.
- If chart TLS is ever enabled, a certificate rotation needs `kubectl rollout restart`; the pods do not
  roll on their own.
