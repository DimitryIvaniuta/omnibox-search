package com.github.dimitryivaniuta.gateway.write.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import com.github.dimitryivaniuta.gateway.write.repo.ContactRepo;
import com.github.dimitryivaniuta.gateway.write.repo.OutboxRepo;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepo contacts;
    private final OutboxRepo outbox;
    private final ObjectMapper om;

    @Transactional
    public ContactResponse create(String tenant, ContactCreateRequest req) {
        UUID id = UUID.randomUUID();
        var c = Contact.builder()
                .id(id)
                .tenantId(tenant)
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .label(req.getLabel())
                .version(0)
                .build();

        contacts.insert(c);

        // emit ContactCreated to outbox
        var evt = Map.of(
                "type", "ContactCreated",
                "tenantId", tenant,
                "contactId", id.toString(),
                "title", req.getFullName(),
                "subtitle", req.getLabel() == null ? "" : req.getLabel(),
                "visible", true,
                "version", 0,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "CONTACT", id.toString(), "ContactCreated", toJson(evt));

        return ContactResponse.builder()
                .id(id.toString())
                .fullName(c.getFullName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .label(c.getLabel())
                .version(0)
                .build();
    }

    @Transactional
    public ContactResponse update(String tenant, UUID id, ContactUpdateRequest req) {
        // read existing (optional)
        var existing = contacts.find(tenant, id).orElseThrow();

        var updated = Contact.builder()
                .id(id)
                .tenantId(tenant)
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .label(req.getLabel())
                .build();

        // optimistic locking (expects current version from client)
        var fresh = contacts.update(updated, req.getVersion());

        // emit ContactUpdated
        var evt = Map.of(
                "type", "ContactUpdated",
                "tenantId", tenant,
                "contactId", id.toString(),
                "title", fresh.getFullName(),
                "subtitle", fresh.getLabel() == null ? "" : fresh.getLabel(),
                "visible", true,
                "version", fresh.getVersion(),
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "CONTACT", id.toString(), "ContactUpdated", toJson(evt));

        return ContactResponse.builder()
                .id(id.toString())
                .fullName(fresh.getFullName())
                .email(fresh.getEmail())
                .phone(fresh.getPhone())
                .label(fresh.getLabel())
                .version(fresh.getVersion())
                .build();
    }

    @Transactional
    public void delete(String tenant, UUID id, long expectedVersion) {
        contacts.softDelete(tenant, id, expectedVersion);

        var evt = Map.of(
                "type", "ContactDeleted",
                "tenantId", tenant,
                "contactId", id.toString(),
                "visible", false,
                "version", expectedVersion + 1,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "CONTACT", id.toString(), "ContactDeleted", toJson(evt));
    }

    private String toJson(Object obj) {
        try { return om.writeValueAsString(obj); }
        catch (Exception e) { throw new RuntimeException("JSON serialize failed", e); }
    }
}
