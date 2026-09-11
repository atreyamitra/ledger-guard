package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookCryptoUnitTest {
    @Test void hmacMatchesKnownAnswer() {
        byte[] input = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        assertThat(HexFormat.of().formatHex(WebhookCrypto.hmac(input, "key")))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }
    @Test void hashMatchesKnownAnswer() {
        assertThat(WebhookCrypto.bodyHash("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
