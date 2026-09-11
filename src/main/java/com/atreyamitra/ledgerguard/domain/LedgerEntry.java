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

    protected LedgerEntry() { }
    public LedgerEntry(UUID accountId, long amountMinor, String currency, String eventId) {
        this.id = UUID.randomUUID(); this.accountId = accountId; this.amountMinor = amountMinor;
        this.currency = currency; this.eventId = eventId; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
