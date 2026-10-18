# Security

Report a vulnerability privately to the repository owner through GitHub's "Report a vulnerability"
form (private vulnerability reporting). Do not open a public issue.

Rules this repo keeps:

- Camunda holds ids and enums only. Variable names are allow-listed (`models/dictionary/variables.yaml`);
  names that look like money or personal data are refused by the lint and by the worker kit at runtime.
- Workers authenticate to providers with their own Keycloak service client (client credentials) over
  https only, sender-constrained by mesh mTLS and checked by audience and caller (`docs/adr/0002-service-authentication-mtls-and-dpop.md`).
  A fixed token and plain http exist only under the `simulator` profile, and then only to loopback.
- A user acting through a UI (process start, task completion via `bpo-process-api`) presents a
  DPoP-bound token; internal calls (workers, event bridge, Kafka) rely on mesh mTLS instead.
- The repository is public: every workflow runs on GitHub-hosted runners with read-only contents
  permission and no secrets; no self-hosted runner may be attached to it.
- Every id a worker puts into a path or a key is a plain token (`Ids`), every provider answer must
  carry its required fields, and a message never writes process variables; anything else stops the
  instance on an incident instead of continuing.
- The engine authenticates people and clients through OIDC. No component uses the chart's demo users,
  their default role grants, or basic authentication.
- Secrets are referenced by Kubernetes secret name only; no values file carries an inline secret.
- Every image is pinned by digest; the chart archive is pinned by sha256.
