package com.github.dimitryivaniuta.gateway.write.api.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * API response DTO for a Transaction aggregate.
 *
 * <p>Reflects the current write-model state and includes mandatory relations
 * to Contact and Listing. ID fields are represented as strings for consistent
 * JSON formatting across the platform.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionResponse {

    /** Transaction identifier (UUID as string). */
    private String id;

    /** Display title used in search and UI. */
    private String title;

    /** Optional secondary line used in search subtitle. */
    private String subtitle;

    /** Monetary amount associated with the transaction (optional). */
    private BigDecimal amount;

    /** Currency code (e.g., USD, EUR) (optional). */
    private String currency;

    /** Business status (e.g., NEW, PENDING, CLOSED) (optional). */
    private String status;

    /** Optimistic locking counter. */
    private long version;

    /** Required relation to Contact (UUID as string). */
    private String contactId;

    /** Required relation to Listing (UUID as string). */
    private String listingId;
}
