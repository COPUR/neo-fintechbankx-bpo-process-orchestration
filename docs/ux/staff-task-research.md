# Staff task screens: proto-personas, hypotheses and wireframes

Companion to `staff-task-ux.md`. Proto-personas are assumptions to test, not research findings; they carry no
real person's data. Wireframes show the screens as built in `ui/bpo-staff-web` unless marked "next round".

## 1. Proto-personas

| Role | Works how | Fears | Needs from the screen |
|---|---|---|---|
| TR FAST payments operations (L1) | night shift, three or four tabs open, several exceptions at once | recording SETTLED on the wrong payment | the payment id on every confirmation; one click to the payment view |
| Payments supervisor | few cases, high stakes | closing an unclear scheme record on a guess | an outcome for "still unclear" (being added to the model by the TR FAST package) |
| Compliance analyst | daily queue, long reading in the case view | coming back to the wrong case; not knowing what ESCALATE does | the case id on the confirmation; the consequence of ESCALATE in words |
| MLRO / compliance officer | the four-eyes checker | finding out after filling the form that they may not decide | the four-eyes block shown on opening the task |

## 2. Hypotheses and how they are measured

1. *Payments operations.* With a task list, SLA badges and a one-click link to the payment view, more TR FAST
   exceptions close within the operations SLA. We will know when, over the first two weeks, decisions within SLA
   are at least 95 % of payments-operations decisions and expiries to the supervisor at most 5 %. The current
   manual handling time in the monolith is the benchmark; it has not been measured yet, so measuring it comes
   first.
2. *Four eyes.* With the block shown before deciding and the case link on the detail, `FOUR_EYES_REQUIRED` and
   `TASK_NOT_FOUND` refusals stay under 1 % of decision attempts.

Measures (Micrometer, through the `TaskEvents` port; no user id, task key or reference as a tag):

| Meter | Tags | Answers |
|---|---|---|
| `bpo.staff.task.decisions` | actor, taskType, decision, sla (`on-time` / `late`) | hypothesis 1 |
| `bpo.staff.task.age` | actor, taskType | time from task creation to decision |
| `bpo.staff.task.refusals` | actor, reason | hypothesis 2 |
| `bpo.staff.task.claims`, `.releases` | actor, taskType | claim churn |

Dashboards belong to the observability repository.

## 3. Weekly check with three staff members

Each week three staff members use the staff web against the simulator profile with three tasks: find the
overdue payment and close it; hand an unclear payment to the supervisor; as a senior, open a case you reviewed
as analyst. Observed: time per task, clicks on the wrong record, cancels in the confirmation. Findings go into a
research log next to this file, started with the first session, the same day and without personal data.

## 4. Wireframes

Task list with filter counts and the new-task notice (built). The table does not change until the user presses
"Update the list".

```
+-- Payments operations ------------------------------------------------------+
| Show: (o) Assigned to me (1)  ( ) Unassigned in my queues (12)  ( ) All (14) |
|                                                               [Refresh]     |
| [i] 3 new tasks arrived. The list stays as it is until you update it.        |
|     [Update the list]                                                        |
| Task               | Task no. | Assignment | Due                  | Actions    |
| Payment exception  | 22517... | Unassigned | [Overdue] 18 Oct 14:02 | Open Claim |
+------------------------------------------------------------------------------+
```

Confirmation that names the record (built).

```
| Decision: ( ) Settled at the scheme  (o) Rejected at the scheme  ( ) Escalate |
| +-------------------------------------------------------------------------+ |
| | Payment PAY-1 will be rejected and its held amount released. Confirm    | |
| | only if the scheme record says rejected.                                | |
| | [Yes, record it for PAY-1]   [Cancel]                                   | |
| +-------------------------------------------------------------------------+ |
```

Four eyes shown on opening (built). No claim and no decision form; Release appears only if the task was
already assigned to the user outside the API.

```
| Senior review · 2251799813685410                                             |
| [!] You held the earlier step of this record, so four eyes keeps you from    |
|     claiming or deciding this task. Another authorised colleague decides it. |
```

Unknown outcome (built). The screen reads the task once and shows one of three answers; it never resends.

```
| [!] decision sent, answer lost
|       task closed (404)        -> "The task is closed: your decision was most likely recorded.
|                                    Do not send it again; check the result on the record's own screen."
|       still open, still mine   -> "Your decision may still be in progress. Wait a minute and open the
|                                    task again; if it is still open, record the decision again."
|       engine still unreachable -> "The outcome is still unknown. Do not send the decision again."
```

Next round: paging for long queues (an opaque cursor and a `{items, next}` response), and a mandatory coded
reason on each decision, chosen from a fixed list with no free text so no personal data reaches the engine.

```
| Reason (required): [ Other                         v ]                       |
```
