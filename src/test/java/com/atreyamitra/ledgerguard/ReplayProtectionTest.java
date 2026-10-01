package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

/** A captured signed request must not be reusable under a different key, time or body. Default window: 300 s. */
class ReplayProtectionTest extends PostgresIntegrationTest {
    private static String secondsFromNow(long offset) { return Long.toString(Instant.now().getEpochSecond() + offset); }

    @Test void freshSignedWebhookSucceeds() throws Exception {
        var id = createAccount();
        assertThat(send(body(id, 1000), key()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000);
    }
    @Test void identicalRequestReplaysSafely() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String ts = now(); String sig = sign(ts, key, body);
        var first = sendSigned(body, key, ts, sig); var second = sendSigned(body, key, ts, sig);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000);
    }
    @Test void retryWithFreshTimestampAndSameKeyStillReplaysTheOriginal() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String ts1 = secondsFromNow(-5); String ts2 = secondsFromNow(0);
        var first = sendSigned(body, key, ts1, sign(ts1, key, body));
        var retry = sendSigned(body, key, ts2, sign(ts2, key, body));
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(count(id)).isEqualTo(1);
    }
    @Test void capturedBodyWithNewKeyAndOldSignatureIsRejected() throws Exception {
        // The attack from the old limitation: replay a captured signed body under a fresh Idempotency-Key.
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String ts = now(); String sig = sign(ts, key, body);
        assertThat(sendSigned(body, key, ts, sig).getStatusCode()).isEqualTo(HttpStatus.OK);
        String newKey = key();
        assertThat(sendSigned(body, newKey, ts, sig).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000); assertThat(claims(newKey)).isZero();
    }
    @Test void changedTimestampWithOldSignatureIsRejected() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String ts = secondsFromNow(-10); String sig = sign(ts, key, body);
        assertThat(sendSigned(body, key, secondsFromNow(0), sig).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero(); assertThat(claims(key)).isZero();
    }
    @Test void changedBodyWithOldSignatureIsRejected() throws Exception {
        var id = createAccount(); String key = key();
        String ts = now(); String sig = sign(ts, key, body(id, 1000));
        assertThat(sendSigned(body(id, 9000), key, ts, sig).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero();
    }
    @Test void staleButCorrectlySignedRequestIsRejectedEvenAfterItWasCommitted() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String old = secondsFromNow(-1000); // window is 300 s
        var rejected = sendSigned(body, key, old, sign(old, key, body));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getBody()).contains("window");
        assertThat(count(id)).isZero(); assertThat(claims(key)).isZero();
        // Same key, now freshly signed, still works: the stale attempt consumed nothing.
        assertThat(send(body, key).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Re-sending the committed request with its original, now-stale signature is refused too.
        assertThat(sendSigned(body, key, old, sign(old, key, body)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isEqualTo(1);
    }
    @Test void excessivelyFutureTimestampIsRejected() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        String future = secondsFromNow(1000);
        assertThat(sendSigned(body, key, future, sign(future, key, body)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero();
    }
    @Test void timestampsInsideTheWindowAreAccepted() throws Exception {
        var id = createAccount(); String body = body(id, 100);
        for (long offset : new long[] {-250, 250}) {
            String key = key(); String ts = secondsFromNow(offset);
            assertThat(sendSigned(body, key, ts, sign(ts, key, body)).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(count(id)).isEqualTo(2);
    }
    @Test void malformedOrMissingTimestampIsRejectedEvenWhenSignedOverIt() throws Exception {
        var id = createAccount(); String body = body(id, 1000); String good = now();
        // Each is validly signed over the exact bad string, so only the format check can refuse it.
        for (String bad : new String[] {"", "abc", "-5", "+" + good, "0" + good, good + ".5", " " + good, good + " ", "1e9", "1".repeat(13), "٣١٤"}) {
            String key = key();
            assertThat(sendSigned(body, key, bad, sign(bad, key, body)).getStatusCode()).as("timestamp '%s'", bad).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        String key = key();
        assertThat(sendSigned(body, key, null, sign(good, key, body)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count(id)).isZero();
    }
    @Test @Timeout(90)
    void concurrentRetriesSignedWithDifferentTimestampsStillCreditOnce() throws Exception {
        var id = createAccount(); String key = key(); String body = body(id, 1000);
        var responses = parallel(20, index -> {
            String ts = secondsFromNow(-index); // every attempt carries its own timestamp and signature
            return sendSigned(body, key, ts, sign(ts, key, body));
        });
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(responses.stream().map(r -> r.getBody()).distinct().count()).isEqualTo(1);
        assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000);
    }
    @Test void conflictingBodyUnderOneKeyIsStillA409WhenBothAreProperlySigned() throws Exception {
        var id = createAccount(); String key = key();
        assertThat(send(body(id, 1000), key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(body(id, 2000), key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count(id)).isEqualTo(1);
    }
}
