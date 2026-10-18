# RUNBOOK-BPO-INCIDENT: an instance stopped on an incident

An incident means a worker failed five times (`retries="5"`) with a transient error or a contract
error. Business rejections never raise incidents; they follow the model's error path.

1. In Operate, open the incident and read the job type and the error message. The message names the
   provider status (for example `503` or `IDEMPOTENCY_KEY_REUSED`), never a payload.
2. Transient (5xx, 429, timeout): check the provider's health and SLO dashboard. When it is healthy
   again, retry the job from Operate. The worker reuses the same idempotency key, so a call that did
   reach the provider before the failure is not repeated.
3. `IDEMPOTENCY_KEY_REUSED` (409): the provider saw the same key with a different request. Do not
   retry. Raise it with the provider's owning squad with the instance key and the job type; this is a
   defect in the worker or the provider.
4. Contract error (any other 4xx): the worker and the provider disagree about the contract. Check
   `contracts/pins/catalog-pins.lock` against the deployed provider version.
5. On the pivot step (`acc.open-account`), never cancel the instance before the provider has
   confirmed whether the account exists for that application.
