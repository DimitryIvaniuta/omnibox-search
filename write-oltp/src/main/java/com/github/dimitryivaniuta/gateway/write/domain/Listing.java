package com.github.dimitryivaniuta.gateway.write.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.*;

/**
 * OLTP aggregate for a Listing.
 * Stored in the write database and projected asynchronously into the read-model.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Listing {
    /**
     * Primary key (UUID).
     */
    private UUID id;

    /**
     * Mls ID.
     */
    private String mlsId;

    /**
     * Multi-tenant isolation key.
     */
    private String tenantId;

    /**
     * Human-readable title used in search and UI.
     */
    private String title;

    /**
     * Optional secondary text used in search subtitle.
     */
    private String subtitle;

    /**
     * Optimistic locking counter.
     */
    private long version;

    /**
     * Audit timestamps.
     */
    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Soft-delete marker (null when active).
     */
    private Instant deletedAt;

    /**
     * Owner contact id (FK).
     */
    private java.util.UUID contactId;
}
