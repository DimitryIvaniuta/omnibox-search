package com.github.dimitryivaniuta.gateway.write.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.service.ListingService;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for Listing CRUD.
 * All endpoints require {@code X-Tenant} header for multi-tenant scoping.
 */
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService svc;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("X-Tenant") String tenant,
            @Valid @RequestBody ListingCreateRequest req) {
        var res = svc.create(tenant, req);
        return ResponseEntity.ok(Map.of("id", res.getId(), "version", res.getVersion()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> update(
            @RequestHeader("X-Tenant") String tenant,
            @PathVariable UUID id,
            @Valid @RequestBody ListingUpdateRequest req) {
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
