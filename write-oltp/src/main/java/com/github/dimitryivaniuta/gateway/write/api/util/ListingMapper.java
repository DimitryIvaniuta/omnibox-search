package com.github.dimitryivaniuta.gateway.write.api.util;

import com.github.dimitryivaniuta.gateway.write.api.dto.ListingCreateRequest;
import com.github.dimitryivaniuta.gateway.write.api.dto.ListingResponse;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;

import java.util.UUID;

public final class ListingMapper {
    public static Listing toEntity(String tenant, ListingCreateRequest req, UUID id) {
        return Listing.builder()
                .id(id)
                .tenantId(tenant)
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .mlsId(req.getMlsId())
                .version(0L)
                .build();
    }

}