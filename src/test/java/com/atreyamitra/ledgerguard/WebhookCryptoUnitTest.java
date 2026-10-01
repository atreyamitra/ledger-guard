package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

/** No Spring, no Docker: pure behaviour of the HMAC / hash / signing-input helpers. */
class WebhookCryptoUnitTest {
    private static final String BODY = "The quick brown fox jumps over the lazy dog";
    private static final String MAC = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"; // known answer, key "key"
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void hmacMatchesKnownAnswer() {
        assertThat(HexFormat.of().formatHex(WebhookCrypto.hmac(bytes(BODY), "key"))).isEqualTo(MAC);
    }
    @Test void hashMatchesKnownAnswer() {
        assertThat(WebhookCrypto.bodyHash(bytes("abc"))).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
    @Test void signedRequestMatchesKnownAnswerComputedByAnIndependentImplementation() {
        // Expected value produced with Python: hmac.new(secret, b"v1\n1700000000\nkey-1\n" + body, sha256).hexdigest()
        byte[] input = WebhookCrypto.signingInput("1700000000", "key-1", bytes("{\"a\":1}"));
        assertThat(input).isEqualTo(bytes("v1\n1700000000\nkey-1\n{\"a\":1}"));
        assertThat(HexFormat.of().formatHex(WebhookCrypto.hmac(input, "test-secret-change-me")))
                .isEqualTo("248997d82b4562289ad8f5dfb0930b0d835f34073ea5447bb2b73401be2fcb91");
    }
    @Test void changingTimestampKeyOrBodyChangesTheSignature() {
        String secret = "test-secret-change-me";
        String base = HexFormat.of().formatHex(WebhookCrypto.hmac(WebhookCrypto.signingInput("1700000000", "k", bytes("b")), secret));
        for (byte[] other : new byte[][] {WebhookCrypto.signingInput("1700000001", "k", bytes("b")),
                WebhookCrypto.signingInput("1700000000", "k2", bytes("b")), WebhookCrypto.signingInput("1700000000", "k", bytes("b2"))})
            assertThat(WebhookCrypto.valid(other, base, secret)).isFalse();
    }
    @Test void signingInputCanBeParsedBackUniquely() {
        // For every accepted timestamp/key (neither can contain '\n'), splitting at the first two newlines after "v1"
        // recovers the exact triple, so two different triples can never share a signing input.
        String[][] triples = {{"0", "k", ""}, {"1700000000", "a", "\nb"}, {"1700000000", "a!b", "v1\n1\nk\nbody"}, {"99", "~", "\n\n\n"}};
        for (String[] t : triples) {
            String message = new String(WebhookCrypto.signingInput(t[0], t[1], bytes(t[2])), StandardCharsets.UTF_8);
            assertThat(message).startsWith("v1\n");
            int first = message.indexOf('\n', 3), second = message.indexOf('\n', first + 1);
            assertThat(message.substring(3, first)).isEqualTo(t[0]);
            assertThat(message.substring(first + 1, second)).isEqualTo(t[1]);
            assertThat(message.substring(second + 1)).isEqualTo(t[2]);
        }
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
}
