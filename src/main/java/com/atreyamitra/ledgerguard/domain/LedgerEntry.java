package com.atreyamitra.ledgerguard.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false, updatable = false) private UUID accountId;
    @Column(name = "amount_minor", nullable = false, updatable = false) private long amountMinor;
    @Column(nullable = false, length = 3, updatable = false) private String currency;
    @Column(name = "event_id", nullable = false, length = 200, updatable = false) private String eventId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    // Nullable only so V2 can be applied to a database that already holds entries; always set by the app.
    @Column(name = "idempotency_key", length = 200, updatable = false) private String idempotencyKey;

    protected LedgerEntry() { }
    public LedgerEntry(UUID accountId, long amountMinor, String currency, String eventId, String idempotencyKey) {
        this.id = UUID.randomUUID(); this.accountId = accountId; this.amountMinor = amountMinor;
        this.currency = currency; this.eventId = eventId; this.idempotencyKey = idempotencyKey; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
