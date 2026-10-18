# POT round 1: local run before the first push (2026-10-18)

Run on a linux/amd64 container with Docker, Temurin 23.0.2, Gradle 8.14.3 (wrapper), Node.js 22,
helm 3.19.0, gitleaks 8.24.3. Head: the commit that adds this file.

| Criterion | Command | Exit | Result |
|---|---|---|---|
| POT1-LINT | `npm test --prefix lint` | 0 | 11 rule tests pass (each rule red on a bad model, green on a good one) |
| POT1-LINT | `npm run lint --prefix lint` | 0 | 2 BPMN and 1 DMN models: 0 errors, 0 warnings |
| POT1-CPT, PIVOT, VARS | `./gradlew --no-daemon clean check` | 0 | cpt 16/16 on camunda/camunda:8.10.4 (coverage: acc-tpp-account-opening 100%, cmp-review-routing 100%, lib-screening-review 96.7%: the error end event `EndEvent_Rejected` is reached by the reject test but the report counts a terminating error end as not completed); worker kit 28/28; onboarding 8/8; archunit-no-camunda 5/5 |
| POT1-CHART | `bash scripts/ci/camunda-chart-render.sh` | 0 | dev 9, prod-ae 10, prod-tr 10 resources; digests only. Red checks: wrong sha256 → exit 1; connectors on → tag-only image and extra workload refused; demo users and role grants restored → refused |
| catalog pins | `bash scripts/ci/check-catalog-pins.sh` | 0 | 8 job types checked. Red checks: unknown operation, pin without pinned expectation, pinned without pin → exit 1 |
| guardrails | publication-guardrails steps, fixture self-test, gitleaks workspace scan | 0 | no findings |

POT1-RUNNER is open: it needs this repository in the fbx-ci runner's REPOS list and `CI_RUNNER` set.
