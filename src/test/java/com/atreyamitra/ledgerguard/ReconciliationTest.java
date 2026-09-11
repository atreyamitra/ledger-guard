package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;

class ReconciliationTest extends PostgresIntegrationTest {
    @Test void healthyAccountsReturnOkTrue() throws Exception {
        createAccount(); // An empty ledger must reconcile to zero.
        var id = createAccount();
        assertThat(send(body(id, 1000), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        var result = reconcile();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(result).get("ok").asBoolean()).isTrue();
    }
    @Test void testOnlyBalanceDriftReturns409AndIsRestored() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Direct SQL only in tests. No production route can set a balance or corrupt the ledger.
        jdbc.update("UPDATE accounts SET balance = balance + 7 WHERE id = ?", id);
        try {
            var result = reconcile();
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            var json = json(result);
            assertThat(json.get("ok").asBoolean()).isFalse();
            assertThat(json.get("drifts").size()).isEqualTo(1);
            var drift = json.get("drifts").get(0);
            assertThat(drift.get("accountId").asText()).isEqualTo(id.toString());
            assertThat(drift.get("balance").asLong()).isEqualTo(1007);
            assertThat(drift.get("ledgerTotal").asLong()).isEqualTo(1000);
        } finally { jdbc.update("UPDATE accounts SET balance = 1000 WHERE id = ?", id); }
        assertThat(reconcile().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void databaseRejectsLedgerUpdatesAndDeletes() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount_minor = 5 WHERE account_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE account_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(count(id)).isEqualTo(1);
        assertThat(reconcile().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    private ResponseEntity<String> reconcile() {
        return http.postForEntity("/api/admin/reconcile", null, String.class);
    }
}
