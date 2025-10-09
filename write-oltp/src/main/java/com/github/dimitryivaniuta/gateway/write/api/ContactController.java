package com.github.dimitryivaniuta.gateway.write.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.service.ContactService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService svc;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("X-Tenant") String tenant,
            @Valid @RequestBody ContactCreateRequest req) {

        var res = svc.create(tenant, req);
        return ResponseEntity.ok(Map.of(
                "id", res.getId(),
                "version", res.getVersion()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponse> update(
            @RequestHeader("X-Tenant") String tenant,
            @PathVariable UUID id,
            @Valid @RequestBody ContactUpdateRequest req) {

        var res = svc.update(tenant, id, req);
        return ResponseEntity.ok(res);
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
