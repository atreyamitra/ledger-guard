package com.atreyamitra.ledgerguard.repository;

import com.atreyamitra.ledgerguard.domain.LedgerEntry;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.UUID;

// Intentionally no update/delete API; inserts use EntityManager.persist.
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {
    List<LedgerEntry> findByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);
    long countByAccountId(UUID accountId);
}
