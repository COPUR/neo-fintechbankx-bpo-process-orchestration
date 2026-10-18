#!/usr/bin/env bash
# ADR 0003: refuses Camunda values that would make the chart call `lookup`. In camunda-platform 15.1.1
# the only `lookup` calls sit behind global.tls.*.autoRollout, so every values file must leave those
# flags off (any case or quoting of true/yes/on is refused) and values-common.yaml must pin all four
# to false.
# Usage: scripts/ci/check-camunda-autorollout.sh <values-dir>
set -euo pipefail

dir="${1:?usage: check-camunda-autorollout.sh <values-dir>}"
fail=0
if grep -inE '^[^#]*autoRollout:[[:space:]]*["'"'"']?(true|yes|on|y)["'"'"']?[[:space:]]*(#.*)?$' "$dir"/values-*.yaml; then
  echo "values files switch on a TLS autoRollout flag (the chart would call lookup)" >&2; fail=1
fi
for f in caBundle orchestration connectors optimize; do
  if ! grep -A2 -E "^    $f:" "$dir/values-common.yaml" | grep -qE '^      autoRollout: false$'; then
    echo "values-common.yaml must pin global.tls.$f.autoRollout: false" >&2; fail=1
  fi
done
exit "$fail"
