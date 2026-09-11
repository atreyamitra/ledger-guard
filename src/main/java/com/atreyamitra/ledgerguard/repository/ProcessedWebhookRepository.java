package com.atreyamitra.ledgerguard.repository;

import com.atreyamitra.ledgerguard.domain.ProcessedWebhook;
import org.springframework.data.repository.Repository;
import java.util.Optional;

public interface ProcessedWebhookRepository extends Repository<ProcessedWebhook, String> {
    Optional<ProcessedWebhook> findById(String key);
}
