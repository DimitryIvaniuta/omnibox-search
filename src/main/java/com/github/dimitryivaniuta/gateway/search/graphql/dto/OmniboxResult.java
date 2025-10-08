package com.github.dimitryivaniuta.gateway.search.graphql.dto;

import java.util.List;
import lombok.Builder;
import lombok.Singular;

@Builder
public record OmniboxResult(
        @Singular("contact") List<SearchHitContact> contacts,
        @Singular("listing") List<SearchHitListing> listings,
        @Singular("referral") List<SearchHitReferral> referrals,
        @Singular("transaction") List<SearchHitTransaction> transactions,
        @Singular("product") List<SearchHitProduct> products,
        @Singular("mailing") List<SearchHitMailing> mailings
) {}