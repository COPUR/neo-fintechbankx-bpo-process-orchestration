#!/usr/bin/env bash
# One command for the local TR FAST POC/POT demo (demo/README.md). Builds the demo image, installs Camunda 8.10.4,
# the payments workers with the simulator and the synthetic sample data into namespace bpo-demo-tr, and prints how
# to open Operate and Tasklist.
#   BPO_DEMO_CLUSTER=kind (default)  create or reuse the kind cluster "bpo-demo"
#   BPO_DEMO_CLUSTER=current         use the current kubectl context; its API server must be on this machine
set -euo pipefail
cd "$(dirname "$0")/.."
ns=bpo-demo-tr
mode="${BPO_DEMO_CLUSTER:-kind}"

case "$mode" in
  kind)
    command -v kind >/dev/null || { echo "kind is required for BPO_DEMO_CLUSTER=kind" >&2; exit 1; }
    kind get clusters 2>/dev/null | grep -qx bpo-demo || kind create cluster --name bpo-demo --wait 180s
    kubectl config use-context kind-bpo-demo >/dev/null ;;
  current) ;;
  *) echo "BPO_DEMO_CLUSTER must be kind or current" >&2; exit 1 ;;
esac

# Never a shared cluster: the API server has to be on this machine.
server=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
if ! [[ "$server" =~ ^https://(127\.0\.0\.1|localhost|\[::1\]):[0-9]{1,5}/?$ ]]; then
  echo "refusing: the current context's API server ($server) is not on this machine" >&2; exit 1
fi

./gradlew -q :payments-demo:bootJar
docker build -q -t bpo-payments-demo:local -f demo/payments-demo/Dockerfile demo/payments-demo
if [ "$mode" = kind ]; then kind load docker-image bpo-payments-demo:local --name bpo-demo; fi
# A cluster whose runtime does not see Docker images (k3s on containerd, for example) gets the image through a
# command that reads an image archive on stdin, e.g. BPO_DEMO_IMAGE_LOAD="sudo k3s ctr images import -".
if [ -n "${BPO_DEMO_IMAGE_LOAD:-}" ]; then docker save bpo-payments-demo:local | sh -c "$BPO_DEMO_IMAGE_LOAD"; fi

kubectl apply -f demo/k8s/00-namespace.yaml
if ! kubectl -n "$ns" get secret camunda-presenter >/dev/null 2>&1; then
  kubectl -n "$ns" create secret generic camunda-presenter --from-literal=credential="$(openssl rand -hex 16)"
fi
kubectl -n "$ns" create configmap demo-models --dry-run=client -o yaml \
  --from-file=models/bpmn/_lib/lib-await-external-status.bpmn --from-file=models/bpmn/pay/pay-instant-exception.bpmn \
  --from-file=models/dmn/psg/psg-status-inquiry-policy.dmn --from-file=models/dmn/pay/pay-exception-routing.dmn \
  --from-file=models/forms/pay-exception-ops-decision.form --from-file=models/forms/pay-exception-supervisor-decision.form \
  | kubectl apply -f -
kubectl -n "$ns" create configmap demo-scenarios --from-file=demo/sample-data/scenarios.json --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$ns" create configmap demo-loader --from-file=demo/sample-data/load.sh --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f demo/k8s/10-camunda.yaml -f demo/k8s/20-payments-demo.yaml -f demo/k8s/30-network-policy.yaml

kubectl -n "$ns" rollout status deployment/camunda --timeout=600s
kubectl -n "$ns" rollout status deployment/bpo-payments-demo --timeout=300s

kubectl -n "$ns" delete job demo-loader --ignore-not-found
kubectl apply -f demo/k8s/40-loader-job.yaml
kubectl -n "$ns" wait --for=condition=complete job/demo-loader --timeout=300s
kubectl -n "$ns" logs job/demo-loader | tail -3

cat <<MSG

Demo is up. In another terminal:
  kubectl -n $ns port-forward svc/camunda 8080:8080
Then open http://localhost:8080/operate and http://localhost:8080/tasklist as user "presenter".
To read the generated password, run
  kubectl -n $ns get secret camunda-presenter -o jsonpath='{.data.credential}' | base64 -d; echo
MSG
