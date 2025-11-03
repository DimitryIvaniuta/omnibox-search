package com.github.dimitryivaniuta.gateway.write.api.dto;

import com.github.dimitryivaniuta.gateway.money.Money;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO exposing public fields of a Listing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingResponse {
    private UUID id;
    private String mlsId;
    private Money price;
    private String title;
    private String subtitle;
    private String contactId;
    private long version;

    public static ListingResponse toResponse(Listing l) {
        return new ListingResponse(
                l.getId(),
                l.getMlsId(),
                l.getPrice(),
                l.getTitle(),
                l.getSubtitle(),
                l.getContactId().toString(),
                l.getVersion()
        );
    }
}