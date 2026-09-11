import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Dependency-free known-answer checks. These do not exercise Spring, PostgreSQL, or HTTP. */
public class OfflineCryptoCheck {
    private static int checks;
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        String body = "The quick brown fox jumps over the lazy dog";
        String expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        check(HexFormat.of().formatHex(WebhookCrypto.hmac(bytes(body), "key")).equals(expected), "HMAC-SHA256 known answer");
        check(WebhookCrypto.valid(bytes(body), expected, "key"), "valid signature accepted");
        check(WebhookCrypto.valid(bytes(body), expected.toUpperCase(java.util.Locale.ROOT), "key"), "uppercase hex accepted");
        check(!WebhookCrypto.valid(bytes(body + " "), expected, "key"), "raw-byte tampering rejected");
        check(!WebhookCrypto.valid(bytes(body), expected, "other-key"), "wrong secret rejected");
        check(!WebhookCrypto.valid(bytes(body), null, "key"), "missing signature rejected");
        check(!WebhookCrypto.valid(bytes(body), "zz".repeat(32), "key"), "malformed hex rejected");
        check(!WebhookCrypto.valid(bytes(body), "f7", "key"), "truncated signature rejected");
        check(WebhookCrypto.bodyHash(bytes("abc")).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA256 known answer");
        check(WebhookCrypto.bodyHash(bytes("{}")).equals(WebhookCrypto.bodyHash(bytes("{}"))), "identical bodies have equal fingerprints");
        check(!WebhookCrypto.bodyHash(bytes("{}")).equals(WebhookCrypto.bodyHash(bytes("{} "))), "whitespace changes fingerprint");
        check(!WebhookCrypto.bodyHash(bytes("{\"amountMinor\":1}")).equals(WebhookCrypto.bodyHash(bytes("{\"amountMinor\":2}"))), "amount changes fingerprint");
        System.out.println(checks + " offline crypto checks passed; integration tests NOT executed.");
    }
}
