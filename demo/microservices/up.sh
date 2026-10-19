#!/usr/bin/env bash
# One command for the local TR FAST microservices demo (demo/microservices/README.md): PostgreSQL, Keycloak,
# Camunda 8.10 from the pinned Helm chart (orchestration cluster, connectors, Management Identity, Camunda Hub),
# the four simulated providers and the real payments workers as separate services, then the synthetic sample data.
#   BPO_DEMO_CLUSTER=docker-desktop (default)  Docker Desktop's built-in Kubernetes, context "docker-desktop"
#                                              (used through a private kubeconfig; your current context stays)
#   BPO_DEMO_CLUSTER=current                   the current kubectl context; its API server must be on this machine
#   BPO_DEMO_IMAGE_LOAD="<command>"            for a cluster that does not see Docker's images: a command that
#                                              reads an image archive on stdin, e.g. "sudo k3s ctr images import -"
set -euo pipefail
cd "$(dirname "$0")/../.."
ns=bpo-demo-ms
mode="${BPO_DEMO_CLUSTER:-docker-desktop}"
. demo/microservices/local-cluster.sh

for tool in docker kubectl helm openssl keytool curl; do
  command -v "$tool" >/dev/null || { echo "$tool is required (see demo/microservices/README.md)" >&2; exit 1; }
done
require_local_cluster "$mode"

# The chart archive the repo pins (deploy/camunda-platform/chart-lock.yaml), refused when its sha256 differs.
chart_url=$(sed -n 's/^url: //p' deploy/camunda-platform/chart-lock.yaml)
chart_sha=$(sed -n 's/^sha256: //p' deploy/camunda-platform/chart-lock.yaml)
cache=demo/microservices/.cache
chart="$cache/$(basename "$chart_url")"
mkdir -p "$cache"
[ -f "$chart" ] || curl -sSfL -o "$chart" "$chart_url"
actual=$( (sha256sum "$chart" 2>/dev/null || shasum -a 256 "$chart") | cut -d' ' -f1)
[ "$actual" = "$chart_sha" ] || { rm -f "$chart"; echo "chart sha256 mismatch: $actual" >&2; exit 1; }

./gradlew -q :payments-demo:bootJar
docker build -q -t bpo-payments-demo:local -f demo/payments-demo/Dockerfile demo/payments-demo
if [ -n "${BPO_DEMO_IMAGE_LOAD:-}" ]; then docker save bpo-payments-demo:local | sh -c "$BPO_DEMO_IMAGE_LOAD"; fi

kubectl apply -f demo/microservices/k8s/00-namespace.yaml
secret_exists() { kubectl -n "$ns" get secret "$1" >/dev/null 2>&1; }
random() { openssl rand -hex 24; }

# Every credential is generated here, once per namespace, and lives only in Kubernetes Secrets.
secret_exists demo-postgres || kubectl -n "$ns" create secret generic demo-postgres \
  --from-literal=postgres="$(random)" --from-literal=keycloak="$(random)" --from-literal=identity="$(random)" \
  --from-literal=hub="$(random)" --from-literal=camunda="$(random)"
secret_exists demo-keycloak || kubectl -n "$ns" create secret generic demo-keycloak --from-literal=admin-credential="$(random)"
secret_exists demo-presenter || kubectl -n "$ns" create secret generic demo-presenter --from-literal=credential="$(openssl rand -hex 12)"
secret_exists demo-clients || kubectl -n "$ns" create secret generic demo-clients \
  --from-literal=svc-bpo-worker-payments="$(random)" --from-literal=demo-loader="$(random)"
secret_exists demo-pusher || kubectl -n "$ns" create secret generic demo-pusher \
  --from-literal=app-key="$(random)" --from-literal=app-secret="$(random)"
secret_exists demo-oidc || kubectl -n "$ns" create secret generic demo-oidc \
  --from-literal=orchestration="$(random)" --from-literal=connectors="$(random)"

# A demo CA (valid 7 days) and a server certificate for Keycloak and each simulated provider.
if ! secret_exists demo-tls-ca; then
  work="$DEMO_TMP/tls"; mkdir -m 700 "$work"
  printf '[req]\ndistinguished_name=dn\n[dn]\n[ca]\nbasicConstraints=critical,CA:true\nkeyUsage=critical,keyCertSign,cRLSign\n' > "$work/ca.cnf"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/ca.key" 2>/dev/null
  openssl req -x509 -new -key "$work/ca.key" -subj "/CN=bpo-demo-ms local CA" -days 7 -config "$work/ca.cnf" -extensions ca -out "$work/ca.crt"
  # A truststore with an unprotected certificate entry and no MAC, so the JVMs read it without a password.
  keytool -J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE -J-Dkeystore.pkcs12.macAlgorithm=NONE -importcert -noprompt \
    -alias bpo-demo-ca -file "$work/ca.crt" -keystore "$work/truststore.p12" -storetype PKCS12 -storepass "$(random)" >/dev/null
  kubectl -n "$ns" create secret generic demo-tls-ca --from-file=ca.crt="$work/ca.crt" --from-file=truststore.p12="$work/truststore.p12"
  for name in keycloak sim-psg-tr sim-acc sim-pay sim-ntf; do
    printf 'subjectAltName=DNS:%s,DNS:%s.%s.svc,DNS:%s.%s.svc.cluster.local\nextendedKeyUsage=serverAuth\nkeyUsage=critical,digitalSignature,keyEncipherment\n' \
      "$name" "$name" "$ns" "$name" "$ns" > "$work/$name.ext"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/$name.key" 2>/dev/null
    openssl req -new -key "$work/$name.key" -subj "/CN=$name" -out "$work/$name.csr"
    openssl x509 -req -in "$work/$name.csr" -CA "$work/ca.crt" -CAkey "$work/ca.key" -CAcreateserial -days 7 \
      -extfile "$work/$name.ext" -out "$work/$name.crt" 2>/dev/null
    kubectl -n "$ns" create secret generic "demo-tls-$name" --from-file=tls.crt="$work/$name.crt" --from-file=tls.key="$work/$name.key"
  done
fi

kubectl -n "$ns" create configmap demo-models --dry-run=client -o yaml \
  --from-file=models/bpmn/_lib/lib-await-external-status.bpmn --from-file=models/bpmn/pay/pay-instant-exception.bpmn \
  --from-file=models/dmn/psg/psg-status-inquiry-policy.dmn --from-file=models/dmn/pay/pay-exception-routing.dmn \
  --from-file=models/forms/pay-exception-ops-decision.form --from-file=models/forms/pay-exception-supervisor-decision.form \
  | kubectl apply -f -
kubectl -n "$ns" create configmap demo-scenarios --from-file=demo/sample-data/scenarios.json --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$ns" create configmap demo-loader --from-file=demo/sample-data/load.sh --dry-run=client -o yaml | kubectl apply -f -

k8s=demo/microservices/k8s
kubectl apply -f "$k8s/50-network-policy.yaml" -f "$k8s/10-postgres.yaml" -f "$k8s/20-keycloak.yaml"
kubectl -n "$ns" rollout status statefulset/postgres --timeout=300s
kubectl -n "$ns" rollout status deployment/keycloak --timeout=600s

echo "installing Camunda 8.10 (this takes several minutes the first time)"
helm upgrade --install camunda "$chart" -n "$ns" -f demo/microservices/values-camunda.yaml --wait --timeout 20m

kubectl -n "$ns" delete job keycloak-clients --ignore-not-found
kubectl apply -f "$k8s/30-keycloak-clients.yaml"
kubectl -n "$ns" wait --for=condition=complete job/keycloak-clients --timeout=300s

kubectl apply -f "$k8s/40-services.yaml"
for d in sim-psg-tr sim-acc sim-pay sim-ntf bpo-worker-payments; do kubectl -n "$ns" rollout status "deployment/$d" --timeout=300s; done

kubectl -n "$ns" delete job demo-loader --ignore-not-found
kubectl apply -f "$k8s/60-loader-job.yaml"
kubectl -n "$ns" wait --for=condition=complete job/demo-loader --timeout=300s
kubectl -n "$ns" logs job/demo-loader | tail -3

cat <<MSG

Demo is up. Publish the UIs on this machine only (127.0.0.1) with, in another terminal:
  demo/microservices/open.sh
Then sign in as "presenter":
  Operate   http://localhost:8080/operate
  Tasklist  http://localhost:8080/tasklist
  Hub (Web Modeler)  http://localhost:8070
  Identity  http://localhost:8084
To read the generated password, run
  kubectl -n $ns get secret demo-presenter -o jsonpath='{.data.credential}' | base64 -d; echo
MSG
