# Contracts

Workers reach domain services only through published catalog contracts (07 §2, guardrail "no
cross-repo imports"). This folder records what the workers expect and which catalog version they
were built against.

- `expectations/<provider>.yaml`: every operation a worker calls, with method, path, request and
  response fields and the idempotency rule. `status: requested` means Governance has not published
  it yet and `simulators/domain-simulator` stands in for it. `status: pinned` means the operation is
  in the catalog and the pin below names the commit.
- `pins/catalog-pins.lock`: one line per pinned operation:
  `<provider> <operationId> <catalog repo> <commit>`.

Every call sends `Authorization: Bearer` (audience per provider, see `docs/identity-requests.md`),
`x-fapi-interaction-id` (one per business key) and, on commands, `x-idempotency-key`. Workers refuse
an answer without its required fields as a contract fault.

`scripts/ci/check-catalog-pins.sh` refuses a job type whose provider operation has no expectation,
an expectation marked pinned without a pin line, and a pin line without an expectation.
When an operation moves to `pinned`, its job type in `models/dictionary/job-types.yaml` changes
`contract: requested` to `contract: pinned` in the same change, and the simulator follows the
published shape.
