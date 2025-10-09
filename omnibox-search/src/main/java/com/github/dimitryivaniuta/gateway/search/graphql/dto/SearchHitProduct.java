package com.github.dimitryivaniuta.gateway.search.graphql.dto;

import lombok.Builder;

@Builder
public record SearchHitProduct(String id, String title, String subtitle, float score, String productId) implements SearchHit {}