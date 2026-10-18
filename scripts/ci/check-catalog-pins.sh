#!/usr/bin/env bash
# Every job type's provider operation has an expectation; every "pinned" expectation has a pin
# line and every pin line names a pinned expectation. Job types marked contract: pinned must point
# at a pinned expectation. Plain text checks: the files are small and their layout is fixed.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
jobs="$root/models/dictionary/job-types.yaml"
exp="$root/contracts/expectations"
lock="$root/contracts/pins/catalog-pins.lock"
fail=0

status_of() { # provider operation -> status or empty
  awk -v op="  $2:" '$0==op{f=1;next} f&&/^  [^ ]/{exit} f&&/^    status:/{print $2;exit}' "$exp/$1.yaml" 2>/dev/null
}

while IFS= read -r line; do
  type="$(sed -E 's/^  ([a-z0-9.-]+):.*/\1/' <<<"$line")"
  provider="$(sed -nE 's/.*provider: ([a-z0-9-]+).*/\1/p' <<<"$line")"
  operation="$(sed -nE 's/.*operation: ([A-Za-z0-9]+).*/\1/p' <<<"$line")"
  contract="$(sed -nE 's/.*contract: ([a-z]+).*/\1/p' <<<"$line")"
  status="$(status_of "$provider" "$operation")"
  if [[ -z "$status" ]]; then
    echo "$type: no expectation for $provider $operation in contracts/expectations/$provider.yaml" >&2; fail=1
  elif [[ "$contract" != "$status" ]]; then
    echo "$type: job type says contract: $contract but the expectation says status: $status" >&2; fail=1
  fi
done < <(grep -E '^  [a-z0-9-]+\.[a-z0-9.-]+: \{' "$jobs")

for f in "$exp"/*.yaml; do
  provider="$(basename "$f" .yaml)"
  awk '/^  [A-Za-z0-9]+:$/{op=$1; sub(":","",op)} /^    status: pinned/{print op}' "$f" | while read -r op; do
    grep -qE "^$provider $op " "$lock" || { echo "$provider $op is pinned but has no line in catalog-pins.lock" >&2; exit 1; }
  done || fail=1
done

while read -r provider op repo commit; do
  [[ -z "${provider:-}" || "$provider" == \#* ]] && continue
  [[ "$(status_of "$provider" "$op")" == pinned ]] || { echo "pin $provider $op has no pinned expectation" >&2; fail=1; }
  [[ "$commit" =~ ^[0-9a-f]{7,40}$ ]] || { echo "pin $provider $op: commit '$commit' is not a sha" >&2; fail=1; }
done < "$lock"

[[ "$fail" == 0 ]] && echo "catalog pins: $(grep -cE '^  [a-z0-9-]+\.[a-z0-9.-]+: \{' "$jobs") job types checked"
exit "$fail"
