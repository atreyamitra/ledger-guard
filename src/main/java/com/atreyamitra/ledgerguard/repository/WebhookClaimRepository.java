package com.atreyamitra.ledgerguard.repository;

import com.atreyamitra.ledgerguard.domain.ProcessedWebhook;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookClaimRepository {
    private final EntityManager entityManager;
    public WebhookClaimRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public void insert(ProcessedWebhook claim) {
        // persist, NOT merge: an assigned key must never overwrite another request's claim.
        entityManager.persist(claim);
        entityManager.flush();
    }
}
