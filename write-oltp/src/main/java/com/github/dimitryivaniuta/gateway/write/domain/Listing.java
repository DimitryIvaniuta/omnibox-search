package com.github.dimitryivaniuta.gateway.write.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.github.dimitryivaniuta.gateway.money.Money;
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
     * Money: amount + currency value
     */
    private Money price = Money.zero("USD");

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
    private Long version = 0L;

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
