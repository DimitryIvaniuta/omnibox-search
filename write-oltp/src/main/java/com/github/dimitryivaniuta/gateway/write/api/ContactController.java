package com.github.dimitryivaniuta.gateway.write.api;

import com.github.dimitryivaniuta.gateway.write.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService svc;

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestHeader("X-Tenant") String tenant,
                                                      @RequestBody Map<String, String> req) {
        var id = svc.create(tenant, req.get("fullName"), req.get("email"), req.get("phone"), req.get("label"));
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    @PutMapping("/{id}")
    public void update(@RequestHeader("X-Tenant") String tenant, @PathVariable UUID id,
                       @RequestBody Map<String, String> req) {
        svc.update(tenant, id, req.get("fullName"), req.get("email"), req.get("phone"), req.get("label"));
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("X-Tenant") String tenant, @PathVariable UUID id) {
        svc.delete(tenant, id);
    }
}