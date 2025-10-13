package com.github.dimitryivaniuta.gateway.write.graphql.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.ListingCreateRequest;
import com.github.dimitryivaniuta.gateway.write.api.dto.ListingResponse;
import com.github.dimitryivaniuta.gateway.write.api.dto.ListingUpdateRequest;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ListingRepo;
import com.github.dimitryivaniuta.gateway.write.graphql.TenantContext;
import com.github.dimitryivaniuta.gateway.write.service.ListingService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ListingGraphqlController {

    private final ListingService listings;
    private final ListingRepo repo;

    @QueryMapping
    public Listing listing(@Argument UUID id) {
        String tenant = TenantContext.get();
        return repo.find(tenant, id).orElse(null);
    }

    @QueryMapping
    public List<Listing> searchListings(@Argument String q,
                                        @Argument Integer first) {
        String tenant = TenantContext.get();
        int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
        return repo.searchByPrefix(tenant, q, limit);
    }

    @MutationMapping
    public Listing createListing(@Argument("input") ListingCreateRequest input) {
        String tenant = TenantContext.get();
        ListingResponse r = listings.create(tenant, input);
        return repo.find(tenant, UUID.fromString(r.getId())).orElse(null);
    }

    @MutationMapping
    public Listing updateListing(@Argument UUID id,
                                 @Argument("input") ListingUpdateRequest input) {
        String tenant = TenantContext.get();
        ListingResponse r = listings.update(tenant, id, input);
        return repo.find(tenant, UUID.fromString(r.getId())).orElse(null);
    }

    @MutationMapping
    public Boolean deleteListing(@Argument UUID id,
                                 @Argument Long version) {
        String tenant = TenantContext.get();
        listings.delete(tenant, id, version);
        return Boolean.TRUE;
    }
}
