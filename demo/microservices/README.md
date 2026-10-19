# TR FAST demo as microservices (Docker Desktop)

The same P2 `pay-instant-exception` demo as [`demo/README.md`](../README.md), but every part runs as its own service
the way the target is cut: the Camunda 8.10 components from the pinned Helm chart, Keycloak as the identity provider,
PostgreSQL, the four simulated providers and the real payments workers. It is a local, throwaway demo with synthetic
data only. It is not the target topology (`deploy/camunda-platform`).

## What runs

| Service | Image | Role |
|---|---|---|
| `camunda-zeebe` | `camunda/camunda` 8.10.4 | Orchestration cluster: broker, gateway, Operate, Tasklist, cluster Identity |
| `camunda-connectors` | `camunda/connectors-bundle` 8.10.0 | Connectors runtime (no connector is used by P2 yet) |
| `camunda-identity` | `camunda/identity` 8.10.0 | Management Identity; creates realm `camunda-platform` in Keycloak |
| `camunda-web-modeler-restapi` | `camunda/hub` 8.10.2 | Camunda Hub (Web Modeler and Console) |
| `camunda-web-modeler-websockets` | `camunda/hub-websockets` 8.10.2 | Hub live collaboration |
| `keycloak` | `keycloak/keycloak` 26.7.5 | OIDC provider for people and services |
| `postgres` | `postgres` 17.6 | Databases of Keycloak, Identity, Hub and the orchestration secondary storage |
| `sim-psg-tr`, `sim-acc`, `sim-pay`, `sim-ntf` | `bpo-payments-demo:local` | Simulated providers: HTTPS, Keycloak token checked (signature, issuer, expiry, audience, caller) |
| `bpo-worker-payments` | `bpo-payments-demo:local` | The real P2 workers on their deployed path: HTTPS providers, Keycloak client-credentials token |

Every image except the locally built one is pinned by digest.

Of the Camunda images on Docker Hub, the demo uses only those released for 8.10. Web Modeler is shipped as
`camunda/hub` and `camunda/hub-websockets` since 8.10, so the `web-modeler-*` images are not needed.
`zeebe-process-test-engine` is a test library; the repo's CPT tests already use it. The community images
(`zeebe-simple-monitor`, `zeeqs`, `zeebe-simple-tasklist`, `zeebe-*-worker`, `zeebe-*hazelcast-exporter`,
`zeebe-operator`), the 1.0 alphas of `zeebe-tasklist` and `iam`, and `connectors-bundle-bash` were last released
between 2017 and 2023. They target Zeebe 0.x and 1.x, so they are not used.

## What is real and what is simulated

The same as the single-pod demo: Camunda, the models and the workers are real. psg-tr, the acc holds, pay and ntf
are simulated because none of them offers these operations yet. The simulators now check tokens the way the
providers will (docs/identity-requests.md). psg-tr and ntf have no Keycloak client yet, so the demo uses the
working audiences `svc-psg-tr` and `svc-ntf`.

Known demo shortcuts:

- The worker and the loader get the orchestration admin role.
- TLS uses a 7-day demo CA that up.sh makes.
- Camunda talks to Keycloak and PostgreSQL over plain HTTP inside the namespace.
- There is no mesh.

## Run it on a Mac

1. Start Docker Desktop. Under Settings › Kubernetes, enable Kubernetes. Under Settings › Resources, give Docker at
   least 6 CPUs and 12 GB of memory.
2. Install the tools: `brew install kubectl helm openssl`. A JDK 23 is needed for `./gradlew` and `keytool`.
3. From the repo root, run:

   ```sh
   demo/microservices/up.sh
   ```

   The first run downloads the chart and the images, and takes 10 to 15 minutes. It also generates every
   credential into Kubernetes Secrets in namespace `bpo-demo-ms`.
4. In a second terminal, run `demo/microservices/open.sh` and leave it running. It publishes the UIs on 127.0.0.1
   only, so Keycloak's admin console and the Camunda UIs cannot be reached from the network you are on, even on
   public Wi-Fi. Then open the UIs and sign in as `presenter`. up.sh prints the command that reads the password.
   - Operate and Tasklist: <http://localhost:8080/operate>, <http://localhost:8080/tasklist>
   - Hub (Web Modeler): <http://localhost:8070>
   - Identity: <http://localhost:8084>
   - Keycloak: <http://localhost:18080/auth>
5. When you are done, run `demo/microservices/down.sh`. It removes the release and the namespace, data included.

`BPO_DEMO_CLUSTER=current` uses another cluster on this machine instead of Docker Desktop, for example k3s or kind.
open.sh works the same there. Both scripts talk to that one cluster through a private copy of its kubeconfig, so
switching context in another terminal does not redirect them. A cluster that does not see Docker's images gets the demo image
through `BPO_DEMO_IMAGE_LOAD`, for example `BPO_DEMO_IMAGE_LOAD="kind load image-archive /dev/stdin"`.
Docker Desktop can also run Kubernetes in kind mode (a `desktop-control-plane` container); its pods then fail with
`ErrImageNeverPull`. Load the image into that node with
`BPO_DEMO_IMAGE_LOAD="docker exec -i desktop-control-plane ctr -n k8s.io images import -"`.

## Sample data and demo flow

The sample data and demo flow are the same as in [`demo/README.md`](../README.md): 9 synthetic FAST payments. Six
finish on their own (4 settled, 2 rejected), and three wait in the `pay-tr-fast-ops` queue for a decision in
Tasklist. In Hub you can open the same models and show the BPMN and DMN.

To show the security part, run `kubectl -n bpo-demo-ms logs deploy/sim-psg-tr`. It shows the audience the simulator
accepts and how many calls it has served. A call without a valid token for that audience and caller gets 401 or 403
and never reaches the simulator. `JwtVerifierTest` and `ProviderSimulatorServiceTest` in `demo/payments-demo` show
those cases.

## Kubernetes patterns used

- Predictable demands: requests and limits on every pod.
- Singleton through `Recreate` and a single replica.
- Stateful service: PostgreSQL and the broker as StatefulSets.
- Batch Jobs for the client setup and the loader.
- Configuration resources: ConfigMaps and Secrets.
- Process containment: non-root users, no capabilities, read-only root where the image allows it, and the
  restricted Pod Security Standard on the namespace.
- Network segmentation: a deny-all policy plus one policy per flow. Only a network plugin that implements
  NetworkPolicy enforces them, such as k3s or kind with Calico. Docker Desktop's built-in Kubernetes ignores them, so
  on the Mac the boundary is ClusterIP services and the 127.0.0.1 port-forwards.
