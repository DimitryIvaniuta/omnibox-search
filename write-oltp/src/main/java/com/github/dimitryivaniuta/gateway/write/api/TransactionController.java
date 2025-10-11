package com.github.dimitryivaniuta.gateway.write.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.service.TransactionService;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for Transaction CRUD.
 * All endpoints require {@code X-Tenant} header for multi-tenant scoping.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService svc;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("X-Tenant") String tenant,
            @Valid @RequestBody TransactionCreateRequest req) {
        var res = svc.create(tenant, req);
        return ResponseEntity.ok(Map.of("id", res.getId(), "version", res.getVersion()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @RequestHeader("X-Tenant") String tenant,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionUpdateRequest req) {
        return ResponseEntity.ok(svc.update(tenant, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant") String tenant,
            @PathVariable UUID id,
            @RequestParam("version") long version) {
        svc.delete(tenant, id, version);
        return ResponseEntity.noContent().build();
    }
}
