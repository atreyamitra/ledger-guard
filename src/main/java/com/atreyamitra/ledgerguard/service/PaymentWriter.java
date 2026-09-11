package com.atreyamitra.ledgerguard.service;

import com.atreyamitra.ledgerguard.api.*;
import com.atreyamitra.ledgerguard.api.ApiModels.*;
import com.atreyamitra.ledgerguard.domain.*;
import com.atreyamitra.ledgerguard.repository.AccountRepository;
import com.atreyamitra.ledgerguard.repository.WebhookClaimRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWriter {
    private final AccountRepository accounts;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final WebhookClaimRepository claims;
    public PaymentWriter(AccountRepository accounts, EntityManager entityManager, ObjectMapper mapper, WebhookClaimRepository claims) {
        this.accounts = accounts; this.entityManager = entityManager; this.mapper = mapper; this.claims = claims;
    }
    @Transactional
    public String credit(String key, String hash, Payment payment) {
        ProcessedWebhook claim = new ProcessedWebhook(key, hash);
        // Unique index serializes competing claims across processes. Any later error rolls this back.
        claims.insert(claim);
        Account account = accounts.findLockedById(payment.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        account.credit(payment.amountMinor());
        LedgerEntry entry = new LedgerEntry(account.getId(), payment.amountMinor(), payment.currency(), payment.eventId());
        entityManager.persist(entry);
        try {
            String response = mapper.writeValueAsString(new PaymentResult(entry.getId(), account.getId(), payment.amountMinor(),
                    payment.currency(), payment.eventId(), account.getBalance()));
            claim.complete(response);
            return response;
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to encode payment result", ex); }
    }
}
