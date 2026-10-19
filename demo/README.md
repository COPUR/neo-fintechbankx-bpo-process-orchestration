# Local TR FAST POC/POT demo

A laptop demo of the Türkiye FAST exception path (P2 `pay-instant-exception` with `lib-await-external-status`)
on Kubernetes. Scope and criteria: `programmes/pot/tr-fast/` and `programmes/poc/tr-fast/`.

**What is real and what is simulated.** Camunda 8.10.4, the P2 models, DMNs and forms, and the
`bpo-worker-payments` job workers are the real ones from this repository. `psg-tr`, `pay`, `ntf` and the `acc`
holds are the domain simulator, running in the worker's JVM on the loopback address: this repository's
`contracts/expectations/*.yaml` mark every one of those operations `requested`. No ledger, scheme or customer
system is involved, and every payment id is synthetic (`demo-tr-...`). This is POC-0 in
`programmes/poc/tr-fast/scope.md`: it shows the process, not the ledger.

**Not the target topology.** Camunda runs as one pod with in-memory H2 storage, the arrangement the process
tests use. Dev and production use the pinned chart in `deploy/camunda-platform/` with OIDC.

## Run

Needs Docker, kubectl, a JDK 23 for `./gradlew`, openssl, and either kind (default) or a local cluster.

```sh
demo/up.sh                           # creates or reuses the kind cluster "bpo-demo"
BPO_DEMO_CLUSTER=current demo/up.sh  # or the current context, if its API server is on this machine
kubectl -n bpo-demo-tr port-forward svc/camunda 8080:8080
kubectl -n bpo-demo-tr get secret camunda-presenter -o jsonpath='{.data.credential}' | base64 -d; echo
```

Open http://localhost:8080/operate and http://localhost:8080/tasklist as `presenter` with that password. The
password is generated per cluster and never stored in the repository. `demo/down.sh` removes everything.

With `BPO_DEMO_CLUSTER=current` the image `bpo-payments-demo:local` must reach the cluster's runtime: Docker
Desktop and OrbStack see it directly; for k3s on containerd set `BPO_DEMO_IMAGE_LOAD="sudo k3s ctr images import -"`.
kind gets it through `kind load`.

## Sample data (`sample-data/scenarios.json`)

| Payments | psg-tr answers | Outcome |
|---|---|---|
| `demo-tr-acsc-001..003` | ACSC | hold captured once, pay marked settled, customer notified |
| `demo-tr-rjct-001..002` | RJCT | hold released, pay marked rejected, customer notified |
| `demo-tr-pdng-001` | PDNG, then ACSC | waits, the gateway event wakes it, the second read settles it |
| `demo-tr-unres-001..003` | PDNG on every read | four reads, then a task in the TR FAST operations queue |

The loader (`sample-data/load.sh`, run as the Job `demo-loader`) deploys the models, starts one P2 instance per
payment, and publishes `Msg_SchemeStatusReported` three times for the waiting ones, the way the event bridge will,
so the demo does not wait for the real PT30S, PT2M and PT10M timers.

## Demo flow

1. Operate: process `pay-instant-exception`, nine instances. Six completed (three settled, two rejected, one
   settled after the event), three waiting at "Check the scheme record".
2. Open a settled instance: the path through `Task_CaptureHold`, and its variables are ids and enums only.
3. Tasklist: the three unresolved payments in the operations queue. Decide one as settled, one as rejected, and
   escalate one to the supervisor queue; finish that one there.
4. `kubectl -n bpo-demo-tr logs deploy/bpo-payments-demo | grep "demo simulator"`: the simulator's call count and
   holds captured.
