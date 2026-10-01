-- One ledger entry per idempotency claim, enforced by the database rather than by application flow:
-- even a buggy code path cannot credit the same claim twice. Nullable so the migration applies to a
-- database that already contains (append-only, therefore un-backfillable) entries; the application
-- always sets it for new entries.
ALTER TABLE ledger_entries
    ADD COLUMN idempotency_key VARCHAR(200) UNIQUE REFERENCES processed_webhooks(idempotency_key);
ALTER TABLE processed_webhooks
    ADD CONSTRAINT processed_webhooks_body_hash_sha256_hex CHECK (body_hash ~ '^[0-9a-f]{64}$');
