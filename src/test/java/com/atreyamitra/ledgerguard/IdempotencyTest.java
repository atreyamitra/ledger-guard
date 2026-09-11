package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyTest extends PostgresIntegrationTest {
    @Test void sequentialIdenticalWebhooksReturnOriginalResponseAndOneEntry() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        var first = send(body, key); var second = send(body, key);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000); assertThat(claims(key)).isEqualTo(1);
    }
    @Test void sameKeyDifferentBodyReturns409() throws Exception {
        var id = createAccount(); String key = key();
        assertThat(send(body(id, 1000), key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(body(id, 2000), key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000);
    }
    @Test void replayReturnsHistoricalBalanceAfterAnotherCredit() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        var original = send(body, key);
        assertThat(original.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(body(id, 500), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        var replay = send(body, key);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertThat(balance(id)).isEqualTo(1500); assertThat(count(id)).isEqualTo(2);
    }
    @Test void whitespaceChangesBodyHashEvenWhenJsonIsEquivalent() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        assertThat(send(body, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(body + " ", key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count(id)).isEqualTo(1);
    }
}
