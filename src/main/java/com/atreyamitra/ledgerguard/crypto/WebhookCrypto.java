package com.atreyamitra.ledgerguard.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class WebhookCrypto {
    private WebhookCrypto() { }
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
