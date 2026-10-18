# 0001. Camunda holds process state only

Status: Proposed (local; Governance numbers the programme ADR).

## Context

Camunda 8.10 keeps every process variable in the engine log and in secondary storage (OpenSearch),
exports it to Operate, Tasklist and Optimize, and keeps it after the instance ends. Money and
personal data there would make the engine a second system of record with its own retention,
access and residency rules. Business rules in FEEL or DMN would move decisions out of the domain
services that are tested and audited for them.

## Decision

1. Variables are ids, enums and timestamps from `models/dictionary/variables.yaml`. Anything else
   is refused by `fbx/variable-allow-list` at lint time and by `OutputVariables` at runtime.
2. DMN is used for routing only (queue, SLA). Hit policy UNIQUE or FIRST; no amount, rate or
   eligibility output.
3. Workers call domain services through published contracts and pass an idempotency key derived
   from the business key and the step. The step marked `bpo:pivot` is the one that cannot be undone;
   nothing after it compensates it.
4. Rejections a domain returns as 422 become BPMN errors `Err_<Code>`; transient failures are retried
   by the engine and end in an incident.
5. Domain repos never import `io.camunda`.

## Consequences

- A user task shows ids only; the analyst opens the case in the owning service's staff UI.
- Domain services need application-level operations (for example "open the account for this
  application") rather than generic ones with amounts or profile fields.
- Process tests can assert the whole variable set of an instance.
