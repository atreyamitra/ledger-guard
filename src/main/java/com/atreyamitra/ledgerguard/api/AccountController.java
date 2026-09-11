package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.api.ApiModels.*;
import com.atreyamitra.ledgerguard.domain.LedgerEntry;
import com.atreyamitra.ledgerguard.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<AccountView> create(@Valid @RequestBody CreateAccount body) {
        AccountView result = service.create(body.ownerName());
        return ResponseEntity.created(URI.create("/api/accounts/" + result.id())).body(result);
    }
    @GetMapping("/{id}") public AccountView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/entries") public List<LedgerEntry> entries(@PathVariable UUID id) { return service.entries(id); }
}
