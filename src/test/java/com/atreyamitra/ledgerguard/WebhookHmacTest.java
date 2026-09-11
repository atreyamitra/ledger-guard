package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookHmacTest extends PostgresIntegrationTest {
    @Test void badSignatureReturns401AndZeroEntries() throws Exception {
        var id = createAccount(); String key = key();
        assertThat(send(body(id, 1000), key, "00".repeat(32)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero(); assertThat(balance(id)).isZero(); assertThat(claims(key)).isZero();
    }
    @Test void goodSignatureReturns200AndOneEntry() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000);
        var account = http.getForEntity("/api/accounts/" + id, String.class);
        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(account).get("balance").asLong()).isEqualTo(1000);
        var entries = http.getForEntity("/api/accounts/" + id + "/entries", String.class);
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(entries).size()).isEqualTo(1);
        assertThat(json(entries).get(0).get("amountMinor").asLong()).isEqualTo(1000);
    }
    @Test void missingAndMalformedSignaturesReturn401() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), key(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(body(id, 1000), key(), "zz".repeat(32)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero();
    }
    @Test void signatureAuthenticatesRawWhitespaceAndReplay() throws Exception {
        var id = createAccount(); String body = body(id, 1000); String key = key();
        assertThat(send(body + " ", key, sign(body)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(body, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(body, key, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isEqualTo(1);
    }
}
