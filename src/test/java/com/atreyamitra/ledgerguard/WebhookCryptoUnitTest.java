package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.api.WebhookController;
import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

/** No Spring, no Docker: pure behaviour of the HMAC / hash helpers. */
class WebhookCryptoUnitTest {
    private static final String BODY = "The quick brown fox jumps over the lazy dog";
    private static final String MAC = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"; // RFC-style known answer, key "key"
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void hmacMatchesKnownAnswer() {
        assertThat(HexFormat.of().formatHex(WebhookCrypto.hmac(bytes(BODY), "key"))).isEqualTo(MAC);
    }
    @Test void hashMatchesKnownAnswer() {
        assertThat(WebhookCrypto.bodyHash(bytes("abc"))).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
    @Test void acceptsCorrectSignatureInEitherCase() {
        assertThat(WebhookCrypto.valid(bytes(BODY), MAC, "key")).isTrue();
        assertThat(WebhookCrypto.valid(bytes(BODY), MAC.toUpperCase(Locale.ROOT), "key")).isTrue();
    }
    @Test void rejectsTamperedBodyWrongSecretMissingTruncatedAndMalformed() {
        assertThat(WebhookCrypto.valid(bytes(BODY + " "), MAC, "key")).isFalse();
        assertThat(WebhookCrypto.valid(bytes(BODY), MAC, "other-key")).isFalse();
        assertThat(WebhookCrypto.valid(bytes(BODY), null, "key")).isFalse();
        assertThat(WebhookCrypto.valid(bytes(BODY), MAC.substring(2), "key")).isFalse();
        assertThat(WebhookCrypto.valid(bytes(BODY), "zz".repeat(32), "key")).isFalse();
        assertThat(WebhookCrypto.valid(bytes(BODY), MAC + "00", "key")).isFalse();
    }
    @Test void bodyHashIsByteSensitive() {
        assertThat(WebhookCrypto.bodyHash(bytes("{}"))).isEqualTo(WebhookCrypto.bodyHash(bytes("{}")));
        assertThat(WebhookCrypto.bodyHash(bytes("{}"))).isNotEqualTo(WebhookCrypto.bodyHash(bytes("{} ")));
    }
    @Test void controllerRefusesMissingOrShortSecret() {
        assertThatThrownBy(() -> new WebhookController(null, null, null, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookController(null, null, null, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new WebhookController(null, null, null, "x".repeat(16))).doesNotThrowAnyException();
    }
}
