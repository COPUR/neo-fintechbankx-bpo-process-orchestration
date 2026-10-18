#!/usr/bin/env bash
# Negative and positive cases for check-camunda-autorollout.sh, run against copies of the real values.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
check="$root/scripts/ci/check-camunda-autorollout.sh"
src="$root/deploy/camunda-platform"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
failures=0

fresh() { rm -rf "$tmp/v"; mkdir "$tmp/v"; cp "$src"/values-*.yaml "$tmp/v/"; }
expect() { # $1 pass|fail, $2 label
  if bash "$check" "$tmp/v" >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [[ "$got" == "$1" ]]; then echo "ok   $2"; else echo "FAIL $2 (expected $1, got $got)"; failures=1; fi
}

fresh; expect pass "repository values"
for v in true True TRUE '"true"' "'TRUE'" yes On; do
  fresh; printf 'global:\n  tls:\n    orchestration:\n      autoRollout: %s\n' "$v" >> "$tmp/v/values-dev.yaml"
  expect fail "values-dev turns autoRollout on ($v)"
done
fresh; sed -i.bak '/^    connectors:/,/autoRollout/d' "$tmp/v/values-common.yaml"; rm -f "$tmp/v"/*.bak
expect fail "values-common drops the connectors pin"
fresh; printf '# autoRollout: true would call lookup\n' >> "$tmp/v/values-dev.yaml"
expect pass "a comment mentioning autoRollout: true"
exit "$failures"
