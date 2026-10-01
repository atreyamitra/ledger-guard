package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.domain.Account;
import com.atreyamitra.ledgerguard.domain.LedgerEntry;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() { }
    public record CreateAccount(@NotBlank @Size(max = 120) String ownerName) { }
    public record AccountView(UUID id, String ownerName, long balance) {
        public static AccountView from(Account a) { return new AccountView(a.getId(), a.getOwnerName(), a.getBalance()); }
    }
    public record Payment(@NotNull UUID accountId, @NotNull @Positive Long amountMinor,
                          @NotNull @Pattern(regexp = "INR") String currency,
                          @NotBlank @Size(max = 200) String eventId) { }
    public record EntryView(UUID id, UUID accountId, long amountMinor, String currency, String eventId, Instant createdAt) {
        public static EntryView from(LedgerEntry e) {
            return new EntryView(e.getId(), e.getAccountId(), e.getAmountMinor(), e.getCurrency(), e.getEventId(), e.getCreatedAt());
        }
    }
    public record PaymentResult(UUID entryId, UUID accountId, long amountMinor,
                                String currency, String eventId, long balance) { }
}
