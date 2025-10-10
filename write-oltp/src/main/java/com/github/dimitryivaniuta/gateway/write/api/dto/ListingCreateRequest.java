package com.github.dimitryivaniuta.gateway.write.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request payload for Listing creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String mlsId;

    /**
     * Optional line shown as subtitle in omnibox.
     */
    private String subtitle;

    /** Contact owner of this listing (must exist in same tenant). */
    @NotNull
    private java.util.UUID contactId;
}