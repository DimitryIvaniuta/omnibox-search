package com.github.dimitryivaniuta.gateway.search.graphql.dto;

import lombok.Builder;

@Builder
public record SearchHitReferral(String id, String title, String subtitle, float score, String referralId) implements SearchHit {}