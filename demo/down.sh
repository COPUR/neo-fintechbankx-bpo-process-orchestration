#!/usr/bin/env bash
# Removes the local demo. With BPO_DEMO_CLUSTER=kind (default) the kind cluster "bpo-demo" is deleted; otherwise only
# the namespace bpo-demo-tr, and only when the current context's API server is on this machine (as in up.sh).
set -euo pipefail
if [ "${BPO_DEMO_CLUSTER:-kind}" = kind ]; then
  kind delete cluster --name bpo-demo
else
  context=$(kubectl config current-context)
  server=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
  if ! [[ "$server" =~ ^https://(127\.0\.0\.1|localhost|\[::1\]):[0-9]{1,5}/?$ ]]; then
    echo "refusing: the API server of context $context ($server) is not on this machine" >&2
    exit 1
  fi
  kubectl --context "$context" delete namespace bpo-demo-tr --ignore-not-found
fi
