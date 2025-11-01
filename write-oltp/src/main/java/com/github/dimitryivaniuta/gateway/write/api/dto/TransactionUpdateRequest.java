package com.github.dimitryivaniuta.gateway.write.api.dto;

import com.github.dimitryivaniuta.gateway.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

/** Request payload for Transaction update with optimistic locking. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionUpdateRequest {
    @NotBlank
    private String title;
    private String subtitle;
    private Money total;
    private String status;

    private java.util.UUID contactId;
    private java.util.UUID listingId;

    @NotNull
    private Long version;
}
