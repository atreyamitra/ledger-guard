# Interview notes — ledger-guard

Each answer is something the code or a test actually shows. Pointers in brackets.

**Why idempotency?** Webhook senders retry on timeouts and network errors, so the same event can arrive twice, or twice at once. Without it, a retry double-credits money. Idempotency makes "apply" safe to repeat: same key + same body → same result, one effect.

**Why database-backed?** In-memory state is per-instance and lost on restart; with two instances behind a load balancer, each has its own memory. The database is the shared component, and it can make the claim and the money change commit atomically. [`processed_webhooks`, `PaymentWriter`]

**Why a unique constraint?** A check-then-insert (`if !exists → insert`) is a race: two requests both see "not exists". A PRIMARY KEY makes the insert itself the decision; PostgreSQL lets exactly one succeed and makes the other wait, then fail. The app pre-check is only a fast path. [`DatabaseConstraintsTest.uniqueClaimIsTheArbiter...`]

**Why pessimistic locking?** Different keys can credit the *same account* at once. Read-modify-write on `balance` would lose updates. `SELECT … FOR UPDATE` serialises them. I removed the lock as an experiment and three tests failed, so it is demonstrably necessary. Optimistic locking (version column + retry) would also work; pessimistic is simpler for a hot row with short transactions. [`AccountRepository.findLockedById`]

**What race are you preventing?** Two: (1) *same key twice* → double credit (stopped by the claim PK); (2) *different keys, same account* → lost update (stopped by the row lock). They are separate problems with separate mechanisms.

**Two instances receive the same webhook?** Both try to insert the claim; PostgreSQL arbitrates. The loser blocks, then gets a unique violation, rolls back, re-reads the winner's committed row and replays its stored response. If the winner rolled back instead, the loser's insert succeeds and it processes normally. [`MultiInstanceTest`, docs/ARCHITECTURE.md]

**Why is `WebhookService` not `@Transactional`?** After a unique violation the transaction is aborted and unusable. Recovery (re-read the winner) must run in a fresh transaction, so the outer method must not wrap the failed one.

**Why HMAC?** Anyone who can reach the URL could otherwise POST credits. A shared-secret MAC proves the sender knows the secret and the body was not altered.

**What exactly does the HMAC authenticate?** The raw request body bytes — nothing else. Not the `Idempotency-Key`, not headers, not time. So a captured valid request can be replayed with a **new** key and would credit again. That is a documented limitation; the fix is to sign a timestamp + key (reject stale), or dedupe on a provider event id. Also: I verify before parsing because re-serialised JSON would not match the signed bytes, and compare with `MessageDigest.isEqual` (constant time).

**Why is body whitespace significant?** The idempotency fingerprint is SHA-256 of raw bytes. Treating "equivalent" JSON as equal needs canonicalisation rules I'd rather not guess; being strict gives a safe `409`.

**Why Testcontainers?** The guarantees come from PostgreSQL behaviour (blocking on unique inserts, row locks, MVCC snapshots, triggers). H2 or mocks would pass while real Postgres fails. Testcontainers gives a real, throwaway PostgreSQL in CI. Docker missing = test failure, not skip.

**What does the concurrency test prove?** That under real simultaneous HTTP load on real PostgreSQL, duplicates credit once, distinct keys don't lose credits, failures leave no claim, reconciliation never sees partial state — and, via mutation, that these tests fail if the lock or the recovery path is removed.

**What doesn't it prove?** Multi-machine deployment, failover, crash-mid-commit, other isolation levels, throughput, every interleaving. Timing is not controlled; the race is probabilistic in the HTTP tests (one deterministic test covers the loser path). I make no performance claims.

**Why `long` minor units?** Floating point can't represent money exactly. Overflow is a domain exception that rolls the transaction back; reconciliation sums as `numeric` so a corrupt total cannot wrap.

**What is reconciliation for?** `balance` is a denormalised total of `ledger_entries`. Reconciliation recomputes and compares in a single SQL statement (one MVCC snapshot), so it can't report false drift mid-transaction. [`reconciliationNeverSeesDrift...`]

**Why an append-only trigger?** Defence in depth: even a future code path using raw SQL can't edit history. A superuser can still disable it.

**How would this become production-grade?** Sign timestamp + key and enforce a replay window; authenticate/authorise admin and read endpoints; TLS and secret management/rotation; request-size limits and rate limiting; idempotency-record TTL and cleanup; pagination; double-entry model with debits; metrics, structured logging and alerts on reconciliation drift; run reconciliation on a schedule against a replica; load testing; handling provider-specific event ids.
