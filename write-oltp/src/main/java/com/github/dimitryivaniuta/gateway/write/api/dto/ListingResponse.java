package com.github.dimitryivaniuta.gateway.write.api.dto;

import lombok.*;

/**
 * Response DTO exposing public fields of a Listing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingResponse {
    private String id;
    private String mlsId;
    private String title;
    private String subtitle;
    private long version;
}