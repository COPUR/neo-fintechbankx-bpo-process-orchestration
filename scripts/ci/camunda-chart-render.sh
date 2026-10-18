#!/usr/bin/env bash
# Renders deploy/camunda-platform for every environment against the locked
# Camunda chart and refuses what the repo rules forbid:
#   - a chart version outside the locked range, or an archive whose sha256 differs;
#   - any container image that is not pinned by digest;
#   - the chart's demo users, or any inline secret value;
#   - any TLS autoRollout flag switched on: those are the chart's only `lookup` calls (ADR 0003);
#   - Connectors, Web Modeler, Hub, Identity or Optimize switched on;
#   - anything reachable from outside the cluster (Ingress, Route, HTTPRoute, GRPCRoute, a LoadBalancer or
#     NodePort Service) and any Secret object: secrets come from the secret store, never from the chart.
# Usage: scripts/ci/camunda-chart-render.sh [chart.tgz]
# Without an argument the archive is downloaded from the URL in chart-lock.yaml.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
dir="$root/deploy/camunda-platform"
lock="$dir/chart-lock.yaml"
work="$dir/.chart"
mkdir -p "$work"

field() { sed -n "s/^$1: *\"\{0,1\}\([^\"]*\)\"\{0,1\}$/\1/p" "$lock"; }
version="$(field version)"
range="$(field versionRange)"
url="$(field url)"
sha="$(field sha256)"

major="${range%%.x}"
case "$version" in
  "$major".*) ;;
  *) echo "chart version $version is outside the locked range $range" >&2; exit 1 ;;
esac

tgz="${1:-$work/camunda-platform-$version.tgz}"
if [[ ! -f "$tgz" ]]; then
  curl -fsSL --retry 3 -o "$tgz" "$url"
fi
actual="$(sha256sum "$tgz" | cut -d' ' -f1)"
if [[ "$actual" != "$sha" ]]; then
  echo "chart archive sha256 $actual does not match the lock $sha" >&2
  exit 1
fi

fail=0
for env_file in "$dir"/values-*.yaml; do
  env="$(basename "$env_file" .yaml)"
  env="${env#values-}"
  [[ "$env" == common ]] && continue
  out="$dir/rendered-$env.yaml"
  helm template camunda "$tgz" --namespace bpo \
    -f "$dir/values-common.yaml" -f "$env_file" > "$out"

  untagged="$(grep -E '^[[:space:]]*image:' "$out" | grep -v '@sha256:' || true)"
  if [[ -n "$untagged" ]]; then
    echo "$env: images not pinned by digest:" >&2; echo "$untagged" >&2; fail=1
  fi
  # Under OIDC the chart drops the demo users but still grants the admin role to a user named
  # "demo", which anyone able to get that username in the realm would inherit.
  if grep -qiE 'demo@demo\.com|username: *"?(demo|connectors)"?$|^[[:space:]]*- *"?(demo|connectors)"?$' "$out"; then
    echo "$env: chart demo users or their default role grants are still rendered" >&2; fail=1
  fi
  if grep -qE '^kind: (Deployment|StatefulSet)$' "$out"; then
    workloads="$(grep -A3 -E '^kind: (Deployment|StatefulSet)$' "$out" | sed -n 's/^  name: //p' | sort -u)"
    for w in $workloads; do
      case "$w" in
        camunda-zeebe|camunda-orchestration|camunda) ;;
        *) echo "$env: unexpected workload $w (only the orchestration cluster is allowed)" >&2; fail=1 ;;
      esac
    done
  fi
  exposed="$(grep -nE '^kind: (Ingress|Route|HTTPRoute|GRPCRoute|TLSRoute|Gateway|Secret)$' "$out" || true)"
  if [[ -n "$exposed" ]]; then
    echo "$env: external routes or Secret objects rendered:" >&2; echo "$exposed" >&2; fail=1
  fi
  if grep -qE '^[[:space:]]*type: *"?(LoadBalancer|NodePort)"?$' "$out"; then
    echo "$env: a Service is exposed as LoadBalancer or NodePort" >&2; fail=1
  fi
  echo "$env: rendered $(grep -c '^kind:' "$out") resources"
done

# ADR 0003: values must keep the chart's lookup paths switched off.
bash "$root/scripts/ci/check-camunda-autorollout.sh" "$dir" || fail=1

if grep -nE 'inlineSecret: *"?[^" ]+' "$dir"/values-*.yaml; then
  echo "values files carry an inline secret" >&2; fail=1
fi
exit "$fail"
