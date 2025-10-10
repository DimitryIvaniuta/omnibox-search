package com.github.dimitryivaniuta.gateway.write.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ContactRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ListingRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.OutboxRepo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for Listings:
 * - CRUD in OLTP
 * - Outbox event emission within the same transaction
 */
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ContactRepo contactRepo;
    private final ListingRepo listingsRepo;
    private final OutboxRepo outbox;
    private final ObjectMapper om;

    @Transactional
    public ListingResponse create(String tenant, ListingCreateRequest req) {
        if (!contactRepo.contactExists(tenant, req.getContactId())) {
            throw new IllegalArgumentException("Contact does not exist in tenant or is deleted: " + req.getContactId());
        }
        var l = Listing.builder()
                .tenantId(tenant)
                .mlsId(req.getMlsId())
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .contactId(req.getContactId())
                .version(0)
                .build();

        UUID id = listingsRepo.insert(l);

        var evt = Map.of(
                "type", "ListingCreated",
                "tenantId", tenant,
                "listingId", id.toString(),
                "mls_id", l.getMlsId(),
                "contactId", req.getContactId().toString(),
                "title", l.getTitle(),
                "subtitle", l.getSubtitle() == null ? "" : l.getSubtitle(),
                "visible", true,
                "version", 0,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "LISTING", id.toString(), "ListingCreated", toJson(evt));

        return ListingResponse.builder()
                .id(id.toString())
                .title(l.getTitle())
                .subtitle(l.getSubtitle())
                .version(0)
                .build();
    }

    @Transactional
    public ListingResponse update(String tenant, UUID id, ListingUpdateRequest req) {
        // If caller wants to change contact, validate it first
        if (req.getContactId() != null && !contactRepo.contactExists(tenant, req.getContactId())) {
            throw new IllegalArgumentException("Contact does not exist in tenant or is deleted: " + req.getContactId());
        }
        var updated = Listing.builder()
                .id(id).tenantId(tenant)
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .contactId(req.getContactId())
                .build();

        var fresh = listingsRepo.update(updated, req.getVersion());

        var evt = Map.of(
                "type", "ListingUpdated",
                "tenantId", tenant,
                "listingId", id.toString(),
                "contactId", (req.getContactId() != null ? req.getContactId().toString() : null),
                "mlsId", fresh.getMlsId(),
                "title", fresh.getTitle(),
                "subtitle", fresh.getSubtitle() == null ? "" : fresh.getSubtitle(),
                "visible", true,
                "version", fresh.getVersion(),
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "LISTING", id.toString(), "ListingUpdated", toJson(evt));

        return ListingResponse.builder()
                .id(id.toString())
                .mlsId(fresh.getMlsId())
                .title(fresh.getTitle())
                .subtitle(fresh.getSubtitle())
                .version(fresh.getVersion())
                .build();
    }

    @Transactional
    public void delete(String tenant, UUID id, long expectedVersion) {
        listingsRepo.softDelete(tenant, id, expectedVersion);

        var evt = Map.of(
                "type", "ListingDeleted",
                "tenantId", tenant,
                "listingId", id.toString(),
                "visible", false,
                "version", expectedVersion + 1,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "LISTING", id.toString(), "ListingDeleted", toJson(evt));
    }

    private String toJson(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }
}
