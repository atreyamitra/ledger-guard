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
    @FunctionalInterface interface Request { ResponseEntity<String> run(int index) throws Exception; }
    private List<ResponseEntity<String>> parallel(int count, Request request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count); CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                    return request.run(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            return responses;
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
