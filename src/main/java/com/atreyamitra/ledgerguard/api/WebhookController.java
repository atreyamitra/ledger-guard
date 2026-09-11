package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.api.ApiModels.Payment;
import com.atreyamitra.ledgerguard.crypto.WebhookCrypto;
import com.atreyamitra.ledgerguard.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
public class WebhookController {
    private final WebhookService service;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final String secret;
    public WebhookController(WebhookService service, ObjectMapper mapper, Validator validator,
                             @Value("${app.webhook.secret}") String secret) {
        if (secret.isBlank()) throw new IllegalArgumentException("Webhook secret must not be blank");
        this.service = service; this.mapper = mapper; this.validator = validator; this.secret = secret;
    }
    @PostMapping(value = "/api/webhooks/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> payment(@RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        // Authentication uses exact transport bytes, before parsing or validating JSON.
        if (!WebhookCrypto.valid(body, signature, secret))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        if (key == null || key.isBlank() || key.length() > 200)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 200 characters");
        Payment payment;
        try { payment = mapper.readValue(body, Payment.class); }
        catch (IOException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment JSON"); }
        if (payment == null || !validator.validate(payment).isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment fields");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.process(key, body, payment));
    }
}
