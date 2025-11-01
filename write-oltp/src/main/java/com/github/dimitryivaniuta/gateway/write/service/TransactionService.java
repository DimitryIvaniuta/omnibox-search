package com.github.dimitryivaniuta.gateway.write.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.money.Money;
import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.domain.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.dimitryivaniuta.gateway.write.domain.repo.ContactRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ListingRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.OutboxRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.TransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for Transactions:
 * - CRUD in OLTP
 * - Outbox event emission within the same transaction
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final ListingRepo listingRepo;
    private final ContactRepo contactRepo;
    private final TransactionRepo transactionRepo;
    private final OutboxRepo outbox;
    private final ObjectMapper om;

    @Transactional
    public TransactionResponse create(String tenant, TransactionCreateRequest req) {
        var t = Transaction.builder()
                .tenantId(tenant)
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .total(Money.of(req.getTotal().amount(),
                        req.getTotal().currency()))
                .status(req.getStatus())
                .contactId(req.getContactId())
                .listingId(req.getListingId())
                .version(0)
                .build();

        UUID id = transactionRepo.insertAndReturnId(t);

        var evt = Map.of(
                "type", "TransactionCreated",
                "tenantId", tenant,
                "transactionId", id.toString(),
                "title", t.getTitle(),
                "subtitle", t.getSubtitle() == null ? "" : t.getSubtitle(),
                "visible", true,
                "version", 0,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "TRANSACTION", id.toString(), "TransactionCreated", toJson(evt));

        return TransactionResponse.builder()
                .id(id.toString())
                .title(t.getTitle())
                .subtitle(t.getSubtitle())
                .total(t.getTotal())
                .status(t.getStatus())
                .contactId(req.getContactId().toString())
                .listingId(req.getListingId().toString())
                .version(0)
                .build();
    }

    @Transactional
    public TransactionResponse update(String tenant, UUID id, TransactionUpdateRequest req) {
        // If relations are provided, validate them
        UUID newContactId = req.getContactId();
        UUID newListingId = req.getListingId();

        if (newContactId != null && !contactRepo.contactExists(tenant, newContactId)) {
            throw new IllegalArgumentException("Contact does not exist in tenant or is deleted: " + newContactId);
        }
        if (newListingId != null && !listingRepo.listingExists(tenant, newListingId)) {
            throw new IllegalArgumentException("Listing does not exist in tenant or is deleted: " + newListingId);
        }
        if (newContactId != null && newListingId != null) {
            UUID link = listingRepo.listingContact(tenant, newListingId);
            if (link == null || !link.equals(newContactId)) {
                throw new IllegalArgumentException("Listing is not linked to the provided Contact in this tenant.");
            }
        }

        var updated = Transaction.builder()
                .id(id).tenantId(tenant)
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .total(Money.of(req.getTotal().amount(),
                        req.getTotal().currency()))
                .status(req.getStatus())
                .contactId(newContactId)   // may be null -> COALESCE in repo keeps current
                .listingId(newListingId)   // may be null -> COALESCE keeps current
                .build();

        var fresh = transactionRepo.update(updated, req.getVersion());

        var evt = new java.util.LinkedHashMap<String, Object>(10);
        evt.put("type", "TransactionUpdated");
        evt.put("tenantId", tenant);
        evt.put("transactionId", id.toString());

        if (fresh.getContactId() != null) {
            evt.put("contactId", fresh.getContactId().toString());
        }
        if (fresh.getListingId() != null) {
            evt.put("listingId", fresh.getListingId().toString());
        }

        evt.put("title", fresh.getTitle());
        evt.put("subtitle", fresh.getSubtitle() == null ? "" : fresh.getSubtitle());
        evt.put("visible", Boolean.TRUE);
        evt.put("version", fresh.getVersion());
        evt.put("occurredAt", java.time.Instant.now().toString());

        outbox.add(tenant, "TRANSACTION", id.toString(), "TransactionUpdated", toJson(evt));

        return TransactionResponse.builder()
                .id(id.toString())
                .title(fresh.getTitle())
                .subtitle(fresh.getSubtitle())
                .total(fresh.getTotal())
                .status(fresh.getStatus())
                .version(fresh.getVersion())
                .build();
    }


    @Transactional(readOnly = true)
    public List<TransactionResponse> find(String tenant, int offset, int limit) {
        return transactionRepo.findPage(tenant, offset, limit).stream()
                .map(TransactionResponse::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse findOne(String tenant, UUID id) {
        var t = transactionRepo.findOne(tenant, id);
        return t == null ? null : TransactionResponse.toResponse(t);
    }


    @Transactional
    public void delete(String tenant, UUID id, long expectedVersion) {
        transactionRepo.softDelete(tenant, id, expectedVersion);

        var evt = Map.of(
                "type", "TransactionDeleted",
                "tenantId", tenant,
                "transactionId", id.toString(),
                "visible", false,
                "version", expectedVersion + 1,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "TRANSACTION", id.toString(), "TransactionDeleted", toJson(evt));
    }

    @Transactional
    public void deleteBulk(String tenant, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return;

        transactionRepo.softDeleteBulk(tenant, ids);

        Map<String, Object> evt = Map.of(
                "tenantId", tenant,
                "transactionIds", ids.stream().map(UUID::toString).toList(),
                "visible", false,
                "occurredAt", Instant.now().toString()
        );
        outbox.add(tenant, "TRANSACTION", null, "TransactionsDeleted", toJson(evt));
    }

    private String toJson(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failed", e);
        }
    }
}
