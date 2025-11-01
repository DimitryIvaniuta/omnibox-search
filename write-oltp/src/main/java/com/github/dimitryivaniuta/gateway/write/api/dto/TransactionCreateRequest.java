package com.github.dimitryivaniuta.gateway.write.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request payload for Transaction creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCreateRequest {

    @NotBlank
    private String title;
    private String subtitle;

    @NotNull
    private MoneyInput total;

    private String status;

    @NotNull
    private java.util.UUID contactId;

    @NotNull
    private java.util.UUID listingId;

}
