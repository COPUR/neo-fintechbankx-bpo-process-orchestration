# Sourced by up.sh and down.sh: refuse any cluster that is not a throwaway one on this machine, then pin every
# later kubectl and helm call to that one cluster through a private, minified kubeconfig, so switching context in
# another terminal mid-run cannot redirect them. The presenter's own current context is left as it was.
require_local_cluster() {
  local context
  case "$1" in
    docker-desktop) context=docker-desktop ;;
    current) context=$(kubectl config current-context) ;;
    *) echo "BPO_DEMO_CLUSTER must be docker-desktop or current" >&2; exit 1 ;;
  esac
  DEMO_TMP=$(mktemp -d)
  trap 'rm -rf "$DEMO_TMP"' EXIT
  kubectl config view --minify --flatten --context "$context" > "$DEMO_TMP/kubeconfig"
  chmod 600 "$DEMO_TMP/kubeconfig"
  export KUBECONFIG="$DEMO_TMP/kubeconfig"
  local server
  server=$(kubectl config view -o jsonpath='{.clusters[0].cluster.server}')
  if ! [[ "$server" =~ ^https://(127\.0\.0\.1|localhost|\[::1\]|kubernetes\.docker\.internal):[0-9]{1,5}/?$ ]]; then
    echo "refusing: the API server of context $context ($server) is not on this machine" >&2
    exit 1
  fi
}
