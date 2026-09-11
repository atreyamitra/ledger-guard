# ledger-guard

A small Java 17 payment credit ledger with authenticated webhooks, persistent idempotency, atomic balance updates, and reconciliation. A portfolio engineering project with fictional data; no financial institution affiliation.

**Verification status:** implementation and tests are included. The complete Maven build and PostgreSQL integration suite have **not run in the build environment** (Maven/Docker unavailable; dependency download blocked). See [VERIFICATION.md](VERIFICATION.md). No CI badge or concurrency success claim is asserted without a passing run.

## Why this design

Payment delivery can be retried and concurrent. A unique database idempotency key makes a committed response replayable without a second credit. SHA-256 fingerprints bind keys to exact body bytes; JSON whitespace changes count as a different body. HMAC-SHA256 authenticates the raw body before parsing, with `MessageDigest.isEqual` for the MAC comparison. Reconciliation independently compares every stored balance with its ledger sum in a single database snapshot.

## Architecture (8 lines)

1. Controllers expose accounts, webhook ingestion, and reconciliation over HTTP.
2. The webhook controller verifies HMAC over raw bytes before parsing or validation.
3. `WebhookService` hashes the bytes and returns an existing stored response when possible.
4. `PaymentWriter` inserts and flushes an idempotency claim inside a Spring transaction.
5. PostgreSQL's unique key arbitrates competing claims across application instances.
6. A pessimistic account lock serializes balance changes for different payment keys.
7. Claim, credit entry, resulting balance, and serialized response commit together; failed writes roll back together.
8. Duplicate recovery happens after rollback; reconciliation uses one SQL aggregate snapshot.

## Prerequisites

- JDK 17 (`java -version`).
- A running Docker daemon for Testcontainers and permission to pull `postgres:16-alpine`.
- Network access to Maven Central on the first build, and Docker Hub for the test image.
- Linux/macOS launcher: `curl`, `unzip`, `sha512sum` (on macOS, install GNU coreutils and expose `sha512sum`). Windows: PowerShell and `mvnw.cmd`.
- PostgreSQL 16 for running the app (tests provision their own container).

The checked-in `mvnw` / `mvnw.cmd` are lightweight distribution launchers for Maven 3.9.9, not generated Apache Wrapper scripts. They download Maven from the configured HTTPS URL and verify its published SHA-512 checksum. Maven itself need not be installed. The checksum comes from the same origin as the distribution; it is an integrity check, not an independently pinned supply-chain attestation. Dependency versions come from Spring Boot 3.3.13's BOM.

## Run

Start a local development database (or provide an existing PostgreSQL instance):

```sh
docker run --name ledger-guard-postgres --rm \
  -e POSTGRES_DB=ledger_guard -e POSTGRES_USER=ledger_guard -e POSTGRES_PASSWORD=ledger_guard \
  -p 127.0.0.1:5432:5432 -d postgres:16-alpine
./mvnw spring-boot:run
```

The default database credentials and webhook secret are local development examples. The app listens on `127.0.0.1:8080`. Flyway creates the schema; Hibernate only validates it. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `WEBHOOK_SECRET`, and optionally `SERVER_ADDRESS` via environment variables. Windows: use `mvnw.cmd spring-boot:run`.

Example using Python 3's standard library (no additional packages):

```sh
python3 scripts/demo.py
```

It creates an account, sends the exact same signed payment twice, checks the responses match, fetches entries, and reconciles. Start the application before running it. The script reads `WEBHOOK_SECRET` when overridden.

## Test

```sh
./mvnw test
```

CI runs `./mvnw -B test` on a GitHub-hosted Ubuntu runner with Java 17 and Docker. No external database configuration is needed for tests. Testcontainers starts one PostgreSQL container per test JVM and applies the real Flyway migration. Tests use real HTTP requests and independent signature generation. Missing Docker is a failure, not a skip. Classes run sequentially; concurrency is explicitly driven inside `ConcurrencyTest`. Unique accounts and keys isolate scenarios. Test-only drift is restored in a `finally` block.

| Test | Contract checked |
| --- | --- |
| `WebhookHmacTest` | Bad/missing/malformed signatures return 401; a valid request creates one entry; raw-byte tampering and unauthenticated retries are rejected |
| `IdempotencyTest` | Same key/body returns the exact original response; changed body returns 409; historical result survives later credits |
| `ConcurrencyTest.twentyParallelIdenticalWebhooksCreditExactlyOnce` | **20 parallel identical signed webhooks**, all 200 with identical responses, one entry, one claim, balance increased once |
| Other `ConcurrencyTest` cases | Different keys do not lose credits; competing bodies under one key yield one accepted body |
| `ReconciliationTest` | Healthy balances, test-only drift returning 409, SQL update/delete protection |
| `ValidationTest` | Nonpositive amounts, unknown accounts, malformed input, unsupported currency, fractions, missing key, and overflow rollback |
| `WebhookCryptoUnitTest` | Fixed known-answer HMAC-SHA256 and SHA-256 vectors |

With dependencies cached, `./mvnw -Dtest=WebhookCryptoUnitTest test` runs the cryptographic unit tests without Docker. With only JDK 17, `./scripts/check-offline.sh` runs the dependency-free crypto checks. **Neither replaces the HTTP/PostgreSQL suite.**

## HTTP contract

| Request | Success | Error |
| --- | --- | --- |
| `POST /api/accounts` with `{"ownerName":"Asha"}` | 201 `{id,ownerName,balance}` plus Location | 400 invalid name |
| `GET /api/accounts/{id}` | 200 `{id,ownerName,balance}` | 404 unknown account |
| `GET /api/accounts/{id}/entries` | 200 ordered ledger lines | 404 unknown account |
| `POST /api/webhooks/payments` | 200 stored payment result | 401 invalid HMAC; 400 invalid fields/key or overflow; 404 unknown account; 409 reused key with changed body |
| `POST /api/admin/reconcile` | 200 `{"ok":true}` | 409 `{"ok":false,"drifts":[{"accountId":"...","balance":1007,"ledgerTotal":1000}]}` |

Webhook headers: `X-Signature` is 64 hexadecimal characters (HMAC-SHA256 of the exact body bytes); `Idempotency-Key` is a nonblank string up to 200 characters. Authenticate every attempt, including replays.

```json
{"accountId":"<uuid>","amountMinor":1000,"currency":"INR","eventId":"evt_123"}
```

Success fields: `entryId`, `accountId`, `amountMinor`, `currency`, `eventId`, `balance`. `balance` on a replay is the **original resulting balance**, not the current account balance. Amounts and balances are Java `long` paise, never floating point. Reconciliation uses PostgreSQL numeric sums so even a corrupted total does not wrap a long.

## Scope and boundaries

- Credit-only, INR-only ledger; not double-entry accounting, settlement, or a production payment system.
- `eventId` is descriptive metadata. Deduplication is **only by Idempotency-Key**; a different key can credit the same event again. The HMAC signs the body, not the key. No timestamp/replay-window scheme is implemented.
- Account creation/reads and reconciliation have no user/admin authorization, matching this small exercise. Keep the default loopback binding; real deployment needs authentication, authorization, TLS, secret management, and ingress limits.
- The ledger has no update/delete repository API. Hibernate marks entries immutable and PostgreSQL rejects UPDATE, DELETE, and TRUNCATE. A privileged database administrator can still alter protections.
- Idempotency response is nullable only while an uncommitted claim is being filled. Application success commits it with the entry and balance; other transactions cannot observe the intermediate record. Out-of-band database writes are outside this guarantee.
- No idempotency expiration, pagination, debit flow, exchange conversion, or OpenAPI dependency. The small API is documented above.

## What reviewers should open first / file map

```text
src/main/java/com/atreyamitra/ledgerguard/
  api/                      HTTP endpoints, DTO validation, error responses
  crypto/WebhookCrypto.java Raw-body HMAC and SHA-256
  domain/                   Account, immutable ledger entry, processed webhook
  repository/               Account locks, insert-only claims, read repositories
  service/PaymentWriter.java Atomic claim + credit + entry + response transaction
  service/WebhookService.java Replay/conflict handling outside failed transactions
  service/ReconciliationService.java Single-snapshot drift query
src/main/resources/db/migration/V1__create_ledger.sql
src/test/java/com/atreyamitra/ledgerguard/ConcurrencyTest.java
src/test/java/com/atreyamitra/ledgerguard/   Remaining integration + unit tests
.github/workflows/ci.yml     Java 17 / Maven test pipeline
scripts/demo.py             Signed request and replay walkthrough
scripts/check-offline.sh    JDK-only crypto checks
HANDOFF.md                  Current state, exact next command, complete file tree
STATUS.md / TODO.md         Checkpoint status and outstanding work
VERIFICATION.md             Observed checks versus unexecuted tests
RESUME_BULLETS.md            Claims limited to observed passing checks
```

Start with `PaymentWriter`, `WebhookService`, the migration, and `ConcurrencyTest` to review the core guarantees and how they are tested.
