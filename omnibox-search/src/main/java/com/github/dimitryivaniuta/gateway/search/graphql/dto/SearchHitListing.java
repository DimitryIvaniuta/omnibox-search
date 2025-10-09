package com.github.dimitryivaniuta.gateway.search.graphql.dto;


import lombok.Builder;

@Builder
public record SearchHitListing(String id, String title, String subtitle, float score, String listingId) implements SearchHit {}