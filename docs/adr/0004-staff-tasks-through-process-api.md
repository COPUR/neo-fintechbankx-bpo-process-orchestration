# 0004. Staff tasks through bpo-process-api and a per-actor staff web

Status: Proposed (local). Needs security-compliance-architect acceptance; the programme ADR number is
Governance's to assign if this binds other repositories.

## Context

User tasks hold ids and enums only (ADR 0001). Camunda Tasklist can show and complete them, but it lets the
completer submit any variables, it does not take DPoP proofs (ADR 0002), and four eyes would depend on group
discipline alone. Staff need the owning service's detail next to the task without Camunda holding it.

## Decision

- Staff work user tasks through `bpo-process-api` (client `svc-bpo-process`) and the staff web `ui/bpo-staff-web`.
  The staff web never calls the engine.
- The API is a backend for the staff web, with one controller per human actor under `/api/v1/actors/<actor>`.
  Use cases sit in `TaskInbox`; the engine is behind `UserTaskPort` (Orchestration Cluster REST API v2).
- Staff calls are DPoP only: the API reads no bearer token, needs `aud` = `svc-bpo-process` and `cnf.jkt`, and
  verifies the proof on every request.
- A decision request carries `decision` and nothing else; the API writes only the task type's decision variable.
- A user sees a task only when it is open, of a type their actor works, and in one of their queue groups;
  everything else is the same 404.
- Four eyes: the completer of a maker task cannot complete the checker task of the same process instance.
- Tasklist stays a staff-network fallback for the same forms; the model owner adds output mappings so a direct
  completion cannot write other variables.

## Consequences

- Each new user task needs a task type, a queue entry, a controller (for a new actor) and labels; a test fails
  until they exist.
- The API depends on the engine's user task search, including completed tasks, for four eyes.
- Identity needs the staff client, the audience and the groups claim before the first deployment.
