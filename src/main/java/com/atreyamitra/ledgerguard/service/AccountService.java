package com.atreyamitra.ledgerguard.service;

import com.atreyamitra.ledgerguard.api.*;
import com.atreyamitra.ledgerguard.api.ApiModels.AccountView;
import com.atreyamitra.ledgerguard.domain.*;
import com.atreyamitra.ledgerguard.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    public AccountService(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts; this.entries = entries;
    }
    @Transactional
    public AccountView create(String ownerName) { return AccountView.from(accounts.save(new Account(ownerName.strip()))); }
    @Transactional(readOnly = true)
    public AccountView get(UUID id) { return AccountView.from(require(id)); }
    @Transactional(readOnly = true)
    public List<LedgerEntry> entries(UUID id) {
        require(id);
        return entries.findByAccountIdOrderByCreatedAtAscIdAsc(id);
    }
    private Account require(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}
