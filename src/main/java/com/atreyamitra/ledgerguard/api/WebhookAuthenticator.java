package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.regex.Pattern;

/**
 * Authenticates a webhook request: HMAC-SHA256 over {@code "v1\n" + timestamp + "\n" + idempotencyKey + "\n" + rawBody},
 * plus a freshness check on the timestamp.
 *
 * <p>Binding the key and timestamp into the MAC means a captured body cannot be resent under a different
 * key or time. Replaying the <em>identical</em> request inside the window is allowed and is exactly what
 * persistent idempotency is for. Authentication failures are all 401; a malformed key is 400.
 */
@Component
public class WebhookAuthenticator {
    static final int MIN_SECRET_LENGTH = 16;
    static final int MAX_TOLERANCE_SECONDS = 3600;
    // Canonical decimal Unix seconds: no sign, no leading zeros, no whitespace, cannot contain '\n'.
    private static final Pattern TIMESTAMP = Pattern.compile("0|[1-9][0-9]{0,11}");
    // Printable ASCII, no whitespace/control characters: cannot contain '\n'. Keeps the signing input unambiguous.
    private static final Pattern KEY = Pattern.compile("[\\x21-\\x7E]{1,200}");

    private final String secret;
    private final long toleranceSeconds;
    private final Clock clock;

    public WebhookAuthenticator(@Value("${app.webhook.secret}") String secret,
                                @Value("${app.webhook.timestamp-tolerance-seconds}") long toleranceSeconds, Clock clock) {
        // A length floor only catches empty/placeholder values; it says nothing about entropy.
        // Use at least 32 random bytes (e.g. `openssl rand -hex 32`).
        if (secret.length() < MIN_SECRET_LENGTH)
            throw new IllegalArgumentException("app.webhook.secret (WEBHOOK_SECRET) must be set to at least "
                    + MIN_SECRET_LENGTH + " characters; there is deliberately no default. Use 32+ random bytes.");
        if (toleranceSeconds < 1 || toleranceSeconds > MAX_TOLERANCE_SECONDS)
            throw new IllegalArgumentException("app.webhook.timestamp-tolerance-seconds must be between 1 and " + MAX_TOLERANCE_SECONDS);
        this.secret = secret; this.toleranceSeconds = toleranceSeconds; this.clock = clock;
    }

    /** @throws ApiException 400 for an unusable Idempotency-Key, 401 for any authentication failure */
    public void authenticate(byte[] body, String key, String timestamp, String signature) {
        if (key == null || !KEY.matcher(key).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1 to 200 printable ASCII characters without spaces");
        if (timestamp == null || !TIMESTAMP.matcher(timestamp).matches())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "X-Timestamp must be Unix time in seconds");
        if (!WebhookCrypto.valid(WebhookCrypto.signingInput(timestamp, key, body), signature, secret))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        // Checked after the MAC: only an authentic (but old or future-dated) request learns why it was refused.
        if (Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp)) > toleranceSeconds)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "X-Timestamp outside the accepted window of " + toleranceSeconds + " seconds");
    }
}
