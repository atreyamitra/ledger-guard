# Resume bullets — limited to passing checks

These claims are based only on the 12 executed offline checks in VERIFICATION.md. The PostgreSQL/HTTP tests have not run; neither bullet claims verified concurrent processing, transaction safety, or reconciliation results.

- Implemented Java 17 raw-body webhook authentication with HMAC-SHA256 and timing-safe MAC comparison; validated a known-answer signature and rejection of altered payloads, incorrect secrets, and malformed signatures using executable checks.
- Implemented SHA-256 request fingerprints for exact-body idempotency matching; verified known-answer hashing, deterministic fingerprints, and detection of amount and whitespace changes with dependency-free Java checks.

These are narrow technical bullets, not a claim that the complete project is production-ready. Expand them only after the committed integration suite passes.
