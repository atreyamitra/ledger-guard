CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(120) NOT NULL CHECK (length(trim(owner_name)) > 0),
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0)
);
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'INR'),
    event_id VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id, created_at, id);
CREATE TABLE processed_webhooks (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    body_hash VARCHAR(64) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
-- Defense in depth: append-only even when a future application path uses SQL.
CREATE FUNCTION reject_ledger_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$;
CREATE TRIGGER ledger_entries_append_only BEFORE UPDATE OR DELETE OR TRUNCATE
ON ledger_entries FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_mutation();
