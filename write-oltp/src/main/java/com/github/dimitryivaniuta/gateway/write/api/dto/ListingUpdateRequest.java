package com.github.dimitryivaniuta.gateway.write.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request payload for Listing update with optimistic locking.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingUpdateRequest {

    @NotBlank
    String mlsId;

    @NotBlank
    private String title;

    private String subtitle;

    /**
     * Required current version of the entity for optimistic locking.
     */
    @NotNull
    private Long version;
}