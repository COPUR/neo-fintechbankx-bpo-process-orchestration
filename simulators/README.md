# Simulators

`domain-simulator` stands in for every provider operation in `contracts/expectations/` while the
operations are `requested`. It runs in-process on a loopback port, keeps idempotency the way the
contracts require (same key and body replays, same key with another body is 409
`IDEMPOTENCY_KEY_REUSED`), and lets a test script failures: fail before the effect, fail with a status,
or lose the response after the effect. When an operation is published, the simulator follows the
published shape and the contract test checks both against the pin.
