package com.atreyamitra.ledgerguard.service;

import com.atreyamitra.ledgerguard.api.*;
import com.atreyamitra.ledgerguard.api.ApiModels.Payment;
import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import com.atreyamitra.ledgerguard.domain.ProcessedWebhook;
import com.atreyamitra.ledgerguard.repository.ProcessedWebhookRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {
    private final PaymentWriter writer;
    private final ProcessedWebhookRepository processed;
    public WebhookService(PaymentWriter writer, ProcessedWebhookRepository processed) {
        this.writer = writer; this.processed = processed;
    }
    // Intentionally NOT transactional: duplicate recovery must run after the writer rolls back.
    public String process(String key, byte[] rawBody, Payment payment) {
        String hash = WebhookCrypto.bodyHash(rawBody);
        var existing = processed.findById(key);
        if (existing.isPresent()) return replay(existing.get(), hash);
        try { return writer.credit(key, hash, payment); }
        catch (DataIntegrityViolationException duplicate) {
            // PostgreSQL waits for the competing INSERT to commit before reporting a unique violation.
            // Do not hide unrelated integrity errors: only recover when a committed claim exists.
            ProcessedWebhook winner = processed.findById(key).orElseThrow(() -> duplicate);
            return replay(winner, hash);
        }
    }
    private String replay(ProcessedWebhook saved, String hash) {
        if (!saved.getBodyHash().equals(hash))
            throw new ApiException(HttpStatus.CONFLICT, "Idempotency-Key already used with a different body");
        if (saved.getResponseBody() == null) throw new IllegalStateException("Incomplete committed webhook record");
        return saved.getResponseBody();
    }
}
