# Models

- File name = process or decision id; ids use the prefixes `Task_`, `Gateway_`, `Event_`,
  `StartEvent_`, `EndEvent_`, `BoundaryEvent_`, `Msg_`, `Timer_`, `Err_`.
- Every service task names a job type from `dictionary/job-types.yaml` (`<context>.<command>`) and
  retries five times; every wait has a timer; every user task has a candidate group.
- Library sub-processes in `bpmn/_lib` carry a `versionTag` and are called with `versionTag` binding.
- `bpo:pivot` marks the step that cannot be undone; there is no compensation in any model.
- `scripts/models/add-diagram.py <file>` regenerates the diagram after a structural edit.
