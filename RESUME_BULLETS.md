# Resume bullets — from the passing CI suite

The full Maven/Testcontainers suite has run in GitHub Actions and passed: 23 tests, 0 failures, 0 errors ([run](https://github.com/atreyamitra/ledger-guard/actions/runs/34613101102/job/103308234354)). These two bullets are drawn from that passing run.

- Built a Spring Boot payment ledger with HMAC-SHA256-authenticated webhooks and PostgreSQL-backed idempotency; proved exactly-once crediting under 20 concurrent identical requests with a JUnit/Testcontainers integration suite (23 tests passing in CI).
- Designed an idempotency scheme combining a unique database claim with a pessimistic account lock inside one transaction, verified by integration tests covering concurrent duplicate webhooks, conflicting payloads on a reused key, and single-snapshot balance reconciliation.

Earlier, narrower bullets (crypto-only, from before the integration suite ran) are kept in git history; see VERIFICATION.md for the full evidence trail.
