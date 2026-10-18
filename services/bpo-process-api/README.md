# bpo-process-api

Planned (`com.bank.bpo.processapi`, client `svc-bpo-process`): the bank's own API to start a process
by business key, query its state, and complete human tasks with four-eyes checks. Channels call this
API, never the engine directly. Its OpenAPI contract is published from `contracts/published/`.
P1 starts from a test today; the API arrives with the first channel that starts P1.
