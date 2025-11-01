package com.github.dimitryivaniuta.gateway.write.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.github.dimitryivaniuta.gateway.money.Money;
import lombok.*;

/** OLTP aggregate for a Transaction record. */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Transaction {
    private UUID id;                  // DB-generated
    private String tenantId;          // multi-tenant scope
    private String title;             // used in search (A-weight)
    private String subtitle;          // used in search (B-weight)
    private Money total;
    private java.util.UUID contactId;   // NOT NULL FK
    private java.util.UUID listingId;   // NOT NULL FK
    private String status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;        // null if active
}
