package com.github.dimitryivaniuta.gateway.search.graphql.dto;

import lombok.Builder;

@Builder
public record SearchHitTransaction(String id, String title, String subtitle, float score, String transactionId) implements SearchHit {}