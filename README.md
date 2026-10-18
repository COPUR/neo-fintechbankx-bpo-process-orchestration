# neo-fintechbankx-bpo-process-orchestration

Business process orchestration for FinTechBankX on Camunda 8.10 (self-managed, one cluster per
jurisdiction). This repo holds the BPMN and DMN models, the job workers that connect them to the
domain services, the simulators the process tests run against, and the Camunda chart values.

## What Camunda may hold

Process state only: ids, enums and timestamps. **No money, no business rules, no personal data.**
Amounts, limits, eligibility and screening decisions stay in the domain service that owns them; a
model only routes on the outcome a domain service returned. The lint rules, the worker kit and the
process tests each enforce part of this (see "Gates" below).

Domain repos never depend on this repo or on Camunda. Workers reach them only through published
catalog contracts (`contracts/`), and `tests/archunit-no-camunda` is the rule proposed for domain
repos to keep `io.camunda` out (`docs/proposals/archunit-no-camunda.md`).

## Layout

| Path | What it holds |
|---|---|
| `models/bpmn/<context>/<process-id>.bpmn` | One process per file, file name = process id. `_lib/` holds versioned library sub-processes. |
| `models/dmn/<context>/` | Routing decisions only (which queue, how long). |
| `models/forms/` | Tasklist forms without personal data. |
| `models/dictionary/` | Allow-lists the lint and the workers share: variables, job types, messages, errors. |
| `lint/` | bpmnlint (recommended + camunda-compat `camunda-cloud-8-10`) and the repo's `fbx` rules, with tests. |
| `services/bpo-worker-kit` | Idempotency keys, provider HTTP with status mapping, output variable filter, job error mapping. |
| `services/bpo-worker-<family>` | One worker module per family. `onboarding` is live; the others have a README until their first model. |
| `services/bpo-process-api`, `services/bpo-event-bridge` | Planned: start/query/human task API, and the Kafka inbox/outbox bridge (ADR-019). |
| `simulators/domain-simulator` | In-process HTTP stand-in for every provider operation the workers call. |
| `tests/cpt` | Camunda Process Tests of every model against the real engine (Testcontainers) and the simulator. |
| `tests/archunit-no-camunda` | The proposed domain-repo rule and its fixtures. |
| `contracts/` | What the workers expect from each provider and the catalog pins. |
| `deploy/camunda-platform` | Chart lock (camunda-platform 15.x) and values per environment. |
| `deploy/helm` | The bpo services' own chart, added with the first deployable service. |
| `programmes/{pot,poc,pov}` | Proof of technology, concept and value: scope, criteria and evidence per round. |
| `docs/` | Local ADRs, runbooks, identity requests and proposals. |

## First process: P1 TPP account opening

`acc-tpp-account-opening` verifies KYC (waiting up to seven days for a pending one, then reading the status from cus again;
the completion message only wakes the instance), screens the
applicant, sends a REVIEW result to `lib-screening-review` (routed by `cmp-review-routing`, with an
analyst task, an escalation task and SLA timers), opens the account exactly once (`acc.open-account`
is the pivot; a lost response is retried with the same idempotency key), offers a Kolay Adres alias
in Türkiye, and notifies the applicant. Every rejection path closes the application and notifies.

## Running locally

```
npm ci --prefix lint && npm test --prefix lint && npm run lint --prefix lint
./gradlew check            # needs Docker for tests/cpt (camunda/camunda:8.10.4); the tests run the
                           # workers under the simulator profile, the only one with a fixed token
bash scripts/ci/check-catalog-pins.sh
bash scripts/ci/camunda-chart-render.sh   # needs helm 3.19+
```

## Gates

| Check | Workflow | What it refuses |
|---|---|---|
| `ci/build` | required-gates | A module that does not compile. |
| `ci/test` | required-gates | A failing unit, contract, ArchUnit or process test; a model or `src/main` change without a test change. |
| `ci/test-arm64` | required-gates | The same tests failing on arm64. |
| `ci/security` | required-gates | A secret in the workspace (gitleaks). |
| `models/lint` | models | A model or decision the lint rules refuse; a job type without a contract expectation. |
| `deploy/camunda-chart` | models | A chart archive other than the locked one, a tag-only image, demo users, an inline secret, a component other than the orchestration cluster. |
| publication guardrails | publication-guardrails | Local paths, personal addresses and secret-like literals in published files. |

Every job runs on GitHub-hosted runners (`ubuntu-latest`, and `ubuntu-24.04-arm` for `ci/test-arm64`).
The repository is public, so no self-hosted runner is ever attached to it and no workflow uses a secret.

## Contributing

See `CONTRIBUTING.md`. Changes reach `main` through a pull request with the three required checks green.
