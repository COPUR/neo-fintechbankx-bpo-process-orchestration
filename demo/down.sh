#!/usr/bin/env bash
# Removes the local demo. With BPO_DEMO_CLUSTER=kind (default) the kind cluster "bpo-demo" is deleted; otherwise only
# the namespace bpo-demo-tr in the current (local) context.
set -euo pipefail
if [ "${BPO_DEMO_CLUSTER:-kind}" = kind ]; then
  kind delete cluster --name bpo-demo
else
  kubectl delete namespace bpo-demo-tr --ignore-not-found
fi
