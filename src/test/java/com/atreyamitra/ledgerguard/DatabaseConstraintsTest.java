package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.api.ApiModels.Payment;
import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import com.atreyamitra.ledgerguard.service.PaymentWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Talks to PostgreSQL directly: the schema must defend the invariants even if application code is wrong. */
class DatabaseConstraintsTest extends PostgresIntegrationTest {
    @Autowired PaymentWriter writer;

    @Test void uniqueClaimIsTheArbiterEvenWhenTheApplicationPreCheckIsBypassed() throws Exception {
        // Calls the transactional writer directly, skipping WebhookService's findById pre-check:
        // this is exactly the state a losing concurrent request is in.
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        assertThat(send(body, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        String hash = WebhookCrypto.bodyHash(body.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> writer.credit(key, hash, new Payment(id, 1000L, "INR", "evt_123")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000); assertThat(claims(key)).isEqualTo(1);
    }
    @Test void ledgerEntryIsLinkedToItsClaimAndCannotBeDuplicated() throws Exception {
        var id = createAccount(); String key = key();
        assertThat(send(body(id, 1000), key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM ledger_entries WHERE account_id = ?", String.class, id)).isEqualTo(key);
        assertThatThrownBy(() -> insertEntry(id, 5, "INR", key)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("idempotency_key");
        assertThatThrownBy(() -> insertEntry(id, 5, "INR", "no-such-claim")).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("violates foreign key");
    }
    @Test void checkConstraintsRejectBadRows() throws Exception {
        var id = createAccount();
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -1 WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO accounts(id, owner_name) VALUES (?, '  ')", UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(id, 0, "INR", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(id, 5, "USD", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO processed_webhooks VALUES (?, 'not-a-hash', NULL, now())", key()))
                .isInstanceOf(DataAccessException.class);
        assertThat(balance(id)).isZero();
    }
    @Test void ledgerRejectsTruncateAndOrphanEntries() throws Exception {
        createAccount();
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger_entries")).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), 5, "INR", null)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("violates foreign key");
    }
    private void insertEntry(UUID account, long amount, String currency, String claimKey) {
        jdbc.update("INSERT INTO ledger_entries(id, account_id, amount_minor, currency, event_id, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), account, amount, currency, "evt_direct", java.sql.Timestamp.from(Instant.now()), claimKey);
    }
}
