# Camunda 8.10 chart values

`chart-lock.yaml` pins camunda-platform **15.1.1** (app 8.10.x) by URL and sha256 and allows only
the 15.x line. (Helm's own `Chart.lock` only records dependencies of a wrapper chart; this repo has
none, so the lock is a plain file the render script reads.)

| File | Use |
|---|---|
| `values-common.yaml` | Every environment: images by digest, OIDC through Keycloak, authorizations on, no demo users, OpenSearch secondary storage, licence from a secret, and only the orchestration cluster (Connectors, Web Modeler, Hub, Identity and Optimize off). |
| `values-dev.yaml` | Shared dev: one broker, one partition. Synthetic data only. |
| `values-prod-ae.yaml`, `values-prod-tr.yaml` | One cluster per jurisdiction: three brokers, six partitions, three replicas, a disruption budget. Needs the commercial licence. |

Test and staging values follow once those environments exist. Hosts are placeholders until
Platform names them.

`scripts/ci/camunda-chart-render.sh` downloads the locked archive, checks its sha256, renders every
environment and refuses tag-only images, the chart's demo users, inline secrets and any workload
other than the orchestration cluster.

`lookup` (ADR 0003): in chart 15.1.1 the only templates that call `lookup` are the TLS checksum
annotations, and each runs only when its `global.tls.*.autoRollout` flag is true. `values-common.yaml`
pins all four flags to false and the render script refuses a values file that turns one on. The platform side (deploy Role without
Secret access, `HELM_DRIVER=configmap`, a guard refusing `lookup`) is proposed in
fintechbankx-platform-delivery-iac-cicd-templates PR #11 and fintechbankx-platform-mesh-security-service-mesh PR #11 and is not merged; this decision depends on them. Camunda's own TLS stays off; transport security is the mesh
(ADR 0002). A certificate rotation, if chart TLS is ever enabled, is a `kubectl rollout restart`.
