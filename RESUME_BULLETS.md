# Resume / LinkedIn evidence

Every line is backed by code and tests in this repo. Verified: 63 tests, 0 failures, on GitHub Actions ([run 10](https://github.com/atreyamitra/ledger-guard/actions/runs/36801224506)); see `VERIFICATION.md`. No performance, scale or production-use claims are made.

**One-line description**
Java/Spring Boot + PostgreSQL service that applies replay-protected, HMAC-signed payment webhooks at most once per idempotency key, verified by Testcontainers concurrency tests.

**Resume bullets**
- Implemented persistent idempotency in Spring Boot/PostgreSQL (primary-key claim row, pessimistic account lock, single transaction) so 20–50 concurrent duplicate webhooks, including requests split across two app instances, credit exactly once; verified by integration tests on real PostgreSQL.
- Closed a webhook replay gap by extending HMAC-SHA256 to cover timestamp + Idempotency-Key + raw body with a configurable freshness window and constant-time comparison; added tests for changed key/body/timestamp, stale, future and malformed timestamps, and an independently computed known-answer vector.
- Designed Flyway-managed schema constraints (CHECKs, FK, unique claim-to-entry link, append-only trigger) and a single-snapshot reconciliation query; wrote 63 JUnit/Testcontainers tests, confirmed by mutation that removing the row lock, duplicate recovery or freshness check fails them, and run them in GitHub Actions.

**LinkedIn project bullets**
- Request authentication: HMAC-SHA256 over an unambiguous `v1 | timestamp | idempotency key | raw body` encoding, plus a freshness window, so a captured request cannot be re-sent under a different key or later.
- Idempotent, transactional crediting: a PostgreSQL primary-key claim arbitrates duplicates across threads and instances; claim, lock, ledger insert, balance and stored response commit atomically, and failures roll back and leave the key reusable.
- Testing: 63 tests on real PostgreSQL via Testcontainers covering concurrent duplicates, conflicting payloads, rollback, replay attempts, DB constraints and reconciliation under concurrent writes.
