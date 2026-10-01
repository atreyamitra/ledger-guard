# Architecture

One Spring Boot service, one PostgreSQL database, three tables. No queues, no caches, no extra services: every guarantee below is enforced by the database and checked by a test.

## Request path

```mermaid
flowchart TD
    A[POST /api/webhooks/payments<br/>X-Signature, Idempotency-Key, raw body] --> B{HMAC-SHA256 over raw bytes<br/>constant-time compare}
    B -- invalid or missing --> R401[401 - nothing written]
    B -- valid --> C{Key 1..200 chars?<br/>strict JSON + bean validation}
    C -- no --> R400[400 - nothing written]
    C -- yes --> D{processed_webhooks row<br/>already committed for key?}
    D -- yes --> E{SHA-256 of body<br/>equals stored hash?}
    E -- yes --> R200R[200 - stored response replayed]
    E -- no --> R409[409 - key reused with a different body]
    D -- no --> T

    subgraph T [One database transaction - PaymentWriter.credit]
        direction TB
        T1[INSERT claim row + flush<br/>PRIMARY KEY = idempotency_key] --> T2[SELECT account FOR UPDATE<br/>pessimistic lock]
        T2 --> T3[balance = addExact balance, amount]
        T3 --> T4[INSERT ledger entry<br/>UNIQUE idempotency_key, FK to claim]
        T4 --> T5[store JSON response on claim row]
    end

    T -- commit --> R200[200 - response]
    T -- unknown account / overflow --> RB[ROLLBACK: claim, entry and balance all vanish<br/>404 / 400]
    T -- unique violation on claim --> F[transaction rolled back]
    F --> G[re-read the winner's committed claim] --> E
```

## Why the pieces are shaped this way

| Piece | Job | Where it is proven |
| --- | --- | --- |
| HMAC over the **raw** bytes, before parsing | Authenticates the sender and the exact payload; a re-serialized body would not verify | `WebhookHmacTest`, `WebhookCryptoUnitTest` |
| `processed_webhooks.idempotency_key` PRIMARY KEY | The arbiter between competing requests, across threads *and* instances. App-level checks are only an optimisation | `ConcurrencyTest`, `MultiInstanceTest`, `DatabaseConstraintsTest` |
| Claim inserted **first**, then flushed | The loser blocks inside PostgreSQL on the winner's uncommitted index entry, so it cannot proceed to touch the balance | `ConcurrencyTest` |
| `SELECT ... FOR UPDATE` on the account | Serialises *different* keys that hit the same account; without it, read-modify-write loses credits | `differentKeysOnSameAccountDoNotLoseCredits` (fails if the lock is removed) |
| One transaction for claim + balance + entry + response | All-or-nothing: a failure after the claim un-claims the key, so a retry can succeed | `ValidationTest.overflowRollsBackClaimEntryAndBalance`, concurrent failure tests |
| `ledger_entries.idempotency_key` UNIQUE + FK | The database itself refuses a second entry for one claim | `DatabaseConstraintsTest` |
| Append-only trigger on `ledger_entries` | UPDATE / DELETE / TRUNCATE rejected even from raw SQL | `ReconciliationTest`, `DatabaseConstraintsTest` |
| Reconciliation as a single SQL statement | One MVCC snapshot, so balance and entries are compared at the same instant; `SUM` is `numeric`, so it cannot wrap a `long` | `ReconciliationTest`, `reconciliationNeverSeesDriftWhileCreditsAreInFlight` |

## Duplicate recovery

`WebhookService.process` is intentionally **not** `@Transactional`.

1. Request B arrives while request A (same key) is mid-transaction. B's pre-check finds nothing committed.
2. B enters `PaymentWriter.credit` and its claim `INSERT` **blocks** on A's uncommitted row.
3. A commits. PostgreSQL now reports a unique violation to B. B's transaction rolls back (no balance or entry change).
4. Back in `WebhookService`, outside any failed transaction, B re-reads the committed claim and returns A's stored response, or 409 if the body hash differs.
5. If A had **rolled back** instead, B's insert succeeds and B runs the whole operation itself. This is why failures never poison a key.

The recovery only fires when a committed claim actually exists; an unrelated integrity error still surfaces as a 500 rather than being masked.

## Data model

```mermaid
erDiagram
    accounts ||--o{ ledger_entries : "account_id (FK)"
    processed_webhooks ||--o| ledger_entries : "idempotency_key (UNIQUE, FK)"
    accounts { uuid id PK; varchar owner_name; bigint balance "CHECK >= 0" }
    ledger_entries { uuid id PK; uuid account_id FK; bigint amount_minor "CHECK > 0"; varchar currency "CHECK = INR"; varchar event_id; timestamptz created_at; varchar idempotency_key "UNIQUE FK" }
    processed_webhooks { varchar idempotency_key PK; varchar body_hash "CHECK sha256 hex"; text response_body; timestamptz created_at }
```

`accounts.balance` is a denormalised running total; `ledger_entries` is the source of truth; reconciliation checks they agree.
