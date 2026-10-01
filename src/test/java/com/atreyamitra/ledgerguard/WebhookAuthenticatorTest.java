package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.api.ApiException;
import com.atreyamitra.ledgerguard.api.WebhookAuthenticator;
import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

/** Deterministic (fixed clock) boundary tests of the freshness window and input rules. */
class WebhookAuthenticatorTest {
    private static final String SECRET = "unit-test-secret-0123456789";
    private static final long NOW = 1_700_000_000L;
    private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    private final WebhookAuthenticator auth = new WebhookAuthenticator(SECRET, 300, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    private static String sign(String ts, String key, byte[] body) {
        return HexFormat.of().formatHex(WebhookCrypto.hmac(WebhookCrypto.signingInput(ts, key, body), SECRET));
    }
    private HttpStatus statusOf(String ts, String key, String sig) {
        try { auth.authenticate(BODY, key, ts, sig); return HttpStatus.OK; }
        catch (ApiException ex) { return ex.getStatus(); }
    }
    private HttpStatus signedStatus(long ts) { String t = Long.toString(ts); return statusOf(t, "k", sign(t, "k", BODY)); }

    @Test void windowBoundariesAreInclusiveAndSymmetric() {
        assertThat(signedStatus(NOW)).isEqualTo(HttpStatus.OK);
        assertThat(signedStatus(NOW - 300)).isEqualTo(HttpStatus.OK);
        assertThat(signedStatus(NOW + 300)).isEqualTo(HttpStatus.OK);
        assertThat(signedStatus(NOW - 301)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signedStatus(NOW + 301)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(signedStatus(0)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void unusableKeysAreBadRequestsAndNeverSignedOver() {
        String ts = Long.toString(NOW);
        for (String key : new String[] {null, "", "a b", "a\nb", "a\tb", "café", "k".repeat(201)})
            assertThat(statusOf(ts, key, "00".repeat(32))).as("key %s", key == null ? "null" : key.length() + " chars").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(signedStatus(NOW)).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(ts, "k".repeat(200), sign(ts, "k".repeat(200), BODY))).isEqualTo(HttpStatus.OK);
    }
    @Test void tamperingWithAnySignedFieldFails() {
        String ts = Long.toString(NOW); String sig = sign(ts, "k", BODY);
        assertThat(statusOf(ts, "k", sig)).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(ts, "other", sig)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf(Long.toString(NOW + 1), "k", sig)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> auth.authenticate("{\"a\":2}".getBytes(StandardCharsets.UTF_8), "k", ts, sig)).isInstanceOf(ApiException.class);
    }
    @Test void startupRejectsWeakConfiguration() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new WebhookAuthenticator("", 300, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookAuthenticator("short", 300, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookAuthenticator(SECRET, 0, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookAuthenticator(SECRET, 3601, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new WebhookAuthenticator(SECRET, 3600, clock)).doesNotThrowAnyException();
    }
}
