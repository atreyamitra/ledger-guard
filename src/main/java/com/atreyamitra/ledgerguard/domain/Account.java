package com.atreyamitra.ledgerguard.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Column(name = "owner_name", nullable = false, length = 120) private String ownerName;
    @Column(nullable = false) private long balance;

    protected Account() { }
    public Account(String ownerName) { this.id = UUID.randomUUID(); this.ownerName = ownerName; }
    public UUID getId() { return id; }
    public String getOwnerName() { return ownerName; }
    public long getBalance() { return balance; }
    public void credit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Credit must be positive");
        balance = Math.addExact(balance, amount);
    }
}
