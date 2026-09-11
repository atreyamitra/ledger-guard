package com.atreyamitra.ledgerguard.api;

import com.atreyamitra.ledgerguard.service.ReconciliationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service = service; }
    @PostMapping("/api/admin/reconcile")
    public ResponseEntity<?> reconcile() {
        var drifts = service.drifts();
        return drifts.isEmpty() ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "drifts", drifts));
    }
}
