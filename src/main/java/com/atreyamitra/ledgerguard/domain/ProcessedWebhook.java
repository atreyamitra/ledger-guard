package com.atreyamitra.ledgerguard.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_webhooks")
public class ProcessedWebhook {
    @Id @Column(name = "idempotency_key", length = 200) private String idempotencyKey;
    @Column(name = "body_hash", nullable = false, length = 64) private String bodyHash;
    @Column(name = "response_body", columnDefinition = "text") private String responseBody;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ProcessedWebhook() { }
    public ProcessedWebhook(String key, String hash) {
        idempotencyKey = key; bodyHash = hash; createdAt = Instant.now();
    }
    public String getBodyHash() { return bodyHash; }
    public String getResponseBody() { return responseBody; }
    public void complete(String response) { responseBody = response; }
}
