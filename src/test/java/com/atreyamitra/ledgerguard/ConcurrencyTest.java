package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest extends PostgresIntegrationTest {
    @Test @Timeout(90)
    void twentyParallelIdenticalWebhooksCreditExactlyOnce() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000); String signature = sign(body);
        var responses = parallel(20, index -> send(body, key, signature));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(responses.stream().map(ResponseEntity::getBody).distinct().count()).isEqualTo(1);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000); assertThat(claims(key)).isEqualTo(1);
    }
    @Test @Timeout(90)
    void differentKeysOnSameAccountDoNotLoseCredits() throws Exception {
        var id = createAccount(); String body = body(id, 1000); String signature = sign(body);
        var responses = parallel(20, index -> send(body, key(), signature));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(count(id)).isEqualTo(20); assertThat(balance(id)).isEqualTo(20000);
    }
    @Test @Timeout(90)
    void competingBodiesWithSameKeyHaveOneWinner() throws Exception {
        var id = createAccount(); String key = key();
        var responses = parallel(20, index -> send(body(id, index % 2 == 0 ? 1000 : 2000), key));
        assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count()).isEqualTo(10);
        assertThat(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count()).isEqualTo(10);
        assertThat(count(id)).isEqualTo(1); assertThat(claims(key)).isEqualTo(1);
        long acceptedAmount = json(responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).findFirst().orElseThrow())
                .get("amountMinor").asLong();
        assertThat(balance(id)).isEqualTo(acceptedAmount);
    }
    @Test @Timeout(90)
    void tenKeysWithFiveDuplicatesEachCreditExactlyTenTimes() throws Exception {
        var id = createAccount(); String body = body(id, 100); String signature = sign(body);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) keys.add(key());
        var responses = parallel(50, index -> send(body, keys.get(index % 10), signature));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(count(id)).isEqualTo(10); assertThat(balance(id)).isEqualTo(1000);
        assertThat(responses.stream().map(ResponseEntity::getBody).distinct().count()).isEqualTo(10);
        assertThat(reconcile().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test @Timeout(90)
    void concurrentDuplicatesForUnknownAccountAllGet404AndLeaveNoClaim() throws Exception {
        String key = key(); String body = body(UUID.randomUUID(), 1000); String signature = sign(body);
        var responses = parallel(20, index -> send(body, key, signature));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(claims(key)).isZero();
    }
    @Test @Timeout(90)
    void concurrentOverflowRollsBackEveryAttempt() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, Long.MAX_VALUE), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        String key = key(); String body = body(id, 1); String signature = sign(body);
        var responses = parallel(20, index -> send(body, key, signature));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(claims(key)).isZero(); assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(Long.MAX_VALUE);
    }
    @Test @Timeout(90)
    void reconciliationNeverSeesDriftWhileCreditsAreInFlight() throws Exception {
        var id = createAccount();
        var done = new java.util.concurrent.atomic.AtomicBoolean(false);
        ExecutorService poller = Executors.newSingleThreadExecutor();
        var results = new java.util.concurrent.CopyOnWriteArrayList<HttpStatusCode>();
        Future<?> polling = poller.submit(() -> { while (!done.get()) results.add(reconcile().getStatusCode()); });
        try {
            var responses = parallel(40, index -> send(body(id, 10), key()));
            assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        } finally { done.set(true); polling.get(30, TimeUnit.SECONDS); poller.shutdownNow(); }
        assertThat(results).isNotEmpty().allSatisfy(s -> assertThat(s).isEqualTo(HttpStatus.OK));
        assertThat(balance(id)).isEqualTo(400);
    }
}
