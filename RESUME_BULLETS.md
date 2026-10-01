# Resume / LinkedIn evidence

Every line is backed by code and tests in this repo; see `VERIFICATION.md` for what was run. No performance, scale or production-use claims are made.

**One-line description**
Java/Spring Boot + PostgreSQL service that applies HMAC-signed payment webhooks exactly once, verified by Testcontainers concurrency tests.

**Resume bullets**
- Implemented a persistent idempotency layer in Spring Boot/PostgreSQL (unique-key claim row, pessimistic account lock, single transaction) so 20–50 concurrent duplicate webhooks credit exactly once; verified with integration tests over real HTTP, including two app instances sharing one database.
- Verified HMAC-SHA256 signatures over raw request bytes before parsing, with constant-time comparison and strict JSON validation; wrote tests for tampered, truncated, malformed and missing signatures and for conflicting bodies on a reused idempotency key (409).
- Designed Flyway-managed schema constraints (CHECKs, FK, unique claim-to-entry link, append-only trigger) plus a single-snapshot reconciliation query; wrote 45 JUnit/Testcontainers tests, confirmed by mutation that removing the row lock or duplicate recovery fails them, and run them in GitHub Actions CI.

**LinkedIn project bullets**
- Exactly-once webhook processing: a PostgreSQL primary-key claim arbitrates duplicates across threads and app instances; duplicate losers roll back and replay the stored response.
- Transaction design: claim, account lock, ledger insert, balance update and stored response commit atomically; failures (unknown account, overflow) roll back and leave the key reusable.
- Testing: 45 tests on real PostgreSQL via Testcontainers, covering concurrent duplicates, conflicting payloads, rollback, DB constraints and reconciliation under concurrent writes.
