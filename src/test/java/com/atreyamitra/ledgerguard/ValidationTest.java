package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ValidationTest extends PostgresIntegrationTest {
    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void nonPositiveAmountReturns400(long amount) throws Exception {
        var id = createAccount(); String key = key();
        assertThat(send(body(id, amount), key).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count(id)).isZero(); assertThat(balance(id)).isZero(); assertThat(claims(key)).isZero();
    }
    @Test void unknownAccountReturns404AndDoesNotConsumeKey() throws Exception {
        String key = key();
        assertThat(send(body(UUID.randomUUID(), 1000), key).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(claims(key)).isZero();
        var id = createAccount();
        assertThat(send(body(id, 1000), key).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void missingKeyAndInvalidJsonReturn400() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send("{", key()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count(id)).isZero();
    }
    @Test void invalidCurrencyFractionalAmountAndMissingFieldsReturn400() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000).replace("INR", "USD"), key()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(body(id, 1000).replace(":1000", ":1.5"), key()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send("{}", key()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count(id)).isZero();
    }
    @Test void overflowRollsBackClaimEntryAndBalance() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, Long.MAX_VALUE), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        String key = key();
        assertThat(send(body(id, 1), key).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balance(id)).isEqualTo(Long.MAX_VALUE); assertThat(count(id)).isEqualTo(1);
        assertThat(claims(key)).isZero();
    }
}
