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

Open point for Platform: the chart's helpers use `lookup` to read a TLS certificate secret when a
`certRef` is configured. These values configure none, so nothing renders through it; the platform
render guard rule "no `lookup`" needs a decision for the vendor chart before TLS refs are added.
