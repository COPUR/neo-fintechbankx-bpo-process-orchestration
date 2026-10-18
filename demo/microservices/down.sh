#!/usr/bin/env bash
# Removes the microservices demo: the Helm release and the namespace with everything in it, data included.
set -euo pipefail
cd "$(dirname "$0")/../.."
. demo/microservices/local-cluster.sh
require_local_cluster "${BPO_DEMO_CLUSTER:-docker-desktop}"
helm uninstall camunda -n bpo-demo-ms --wait 2>/dev/null || true
kubectl delete namespace bpo-demo-ms --ignore-not-found --wait=true
