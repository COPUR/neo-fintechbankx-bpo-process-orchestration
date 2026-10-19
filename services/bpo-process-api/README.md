# bpo-process-api

Staff task API behind the staff web (`com.bank.bpo.processapi`, client `svc-bpo-process`). One controller per
human actor; the rules are in `TaskInbox`; the engine is reached through `UserTaskPort` (Orchestration Cluster
REST API v2). Design: `docs/ux/staff-task-ux.md`, `docs/adr/0004-staff-tasks-through-process-api.md`.

| Route (per actor: `compliance-analyst`, `compliance-senior`, `payments-operations`, `payments-supervisor`) | Does |
|---|---|
| `GET /api/v1/me` | the user name and the actors the token's groups allow |
| `GET /api/v1/actors/{actor}/tasks?filter=mine\|open\|all&limit=1..100&cursor=&createdAfter=` | one page `{items, next, counts, asOf}` of the actor's open tasks in the user's queues, earliest due first; `next` is the cursor of the following page, `counts` the totals per filter |
| `GET /api/v1/actors/{actor}/tasks/{taskKey}` | one task with its read-only reference (for example `caseId`) |
| `POST .../{taskKey}/claim`, `POST .../{taskKey}/release` | assign to the user without override; give back |
| `POST .../{taskKey}/decision` with `{"decision": "...", "reason": "..."}` | complete with the decision and `decisionReason` only (four eyes where set) |

Refusals are RFC 9457 problems with a `code`: `NOT_YOUR_ACTOR` 403, `TASK_NOT_FOUND` 404,
`TASK_CLAIMED_BY_OTHER`, `TASK_NOT_ASSIGNED_TO_YOU`, `FOUR_EYES_REQUIRED` 409, `DECISION_NOT_ALLOWED` 422,
`REASON_NOT_ALLOWED` 422, `BODY_NOT_ALLOWED`, `QUERY_NOT_ALLOWED` 400, `ENGINE_UNAVAILABLE` 503,
`OUTCOME_UNKNOWN` 504. Only `Authorization: DPoP` with a valid proof is read.

Configuration comes from the environment (`BPO_STAFF_ISSUER_URI`, `BPO_STAFF_JWK_SET_URI`,
`BPO_ENGINE_REST_URL`, `BPO_AUTH_TOKEN_URI`, `BPO_AUTH_CLIENT_CREDENTIAL`, `BPO_AUTH_SCOPE`); there are no
working defaults. Not deployable yet: the chart arrives with the identity client and the engine authorizations.
