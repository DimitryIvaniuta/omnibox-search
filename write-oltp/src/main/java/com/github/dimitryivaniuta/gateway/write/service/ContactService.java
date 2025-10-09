package com.github.dimitryivaniuta.gateway.write.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import com.github.dimitryivaniuta.gateway.write.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final ContactRepo contacts;
    private final OutboxRepo outbox;
    private final ObjectMapper om;

    @Transactional
    public UUID create(String tenant, String fullName, String email, String phone, String label) {
        UUID id = UUID.randomUUID();
        var c = Contact.builder().id(id).tenantId(tenant).fullName(fullName).email(email).phone(phone)
                .label(label).version(0).build();
        contacts.upsert(c);
        var evt = Map.of("type", "ContactCreated", "tenantId", tenant, "contactId", id.toString(),
                "title", fullName, "subtitle", (label == null ? "" : label),
                "visible", true, "version", 0, "occurredAt", Instant.now().toString());
        outbox.add(tenant, "CONTACT", id.toString(), "ContactCreated", write(evt));
        return id;
    }

    @Transactional
    public void update(String tenant, UUID id, String fullName, String email, String phone, String label) {
        var c = contacts.find(tenant, id).orElseThrow();
        c.setFullName(fullName);
        c.setEmail(email);
        c.setPhone(phone);
        c.setLabel(label);
        contacts.upsert(c);
        var evt = Map.of("type", "ContactUpdated", "tenantId", tenant, "contactId", id.toString(),
                "title", fullName, "subtitle", (label == null ? "" : label),
                "visible", true, "version", c.getVersion() + 1, "occurredAt", Instant.now().toString());
        outbox.add(tenant, "CONTACT", id.toString(), "ContactUpdated", write(evt));
    }


    @Transactional
    public void delete(String tenant, UUID id) {
        var c = contacts.find(tenant, id).orElseThrow();
        c.setDeletedAt(Instant.now());
        contacts.upsert(c);
        var evt = Map.of("type", "ContactDeleted", "tenantId", tenant, "contactId", id.toString(),
                "visible", false, "version", c.getVersion() + 1, "occurredAt", Instant.now().toString());
        outbox.add(tenant, "CONTACT", id.toString(), "ContactDeleted", write(evt));
    }


    private String write(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}