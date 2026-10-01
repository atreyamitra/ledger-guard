package com.atreyamitra.ledgerguard.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class WebhookCrypto {
    private WebhookCrypto() { }
    /**
     * Bytes covered by the MAC: {@code "v1\n" + timestamp + "\n" + idempotencyKey + "\n" + body}.
     * Unambiguous only because callers guarantee the timestamp is canonical decimal digits and the key
     * is printable ASCII (0x21-0x7E): neither can contain '\n', so the first two newlines after "v1"
     * always delimit them and the remainder is the body. See {@code WebhookAuthenticator}.
     */
    public static byte[] signingInput(String timestamp, String idempotencyKey, byte[] body) {
        byte[] head = ("v1\n" + timestamp + "\n" + idempotencyKey + "\n").getBytes(StandardCharsets.US_ASCII);
        byte[] message = new byte[head.length + body.length];
        System.arraycopy(head, 0, message, 0, head.length);
        System.arraycopy(body, 0, message, head.length, body.length);
        return message;
    }
    public static byte[] hmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("HMAC unavailable", ex); }
    }
    public static boolean valid(byte[] body, String signature, String secret) {
        if (signature == null || signature.length() != 64) return false;
        try {
            byte[] supplied = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(hmac(body, secret), supplied);
        } catch (IllegalArgumentException ex) { return false; }
    }
    public static String bodyHash(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
