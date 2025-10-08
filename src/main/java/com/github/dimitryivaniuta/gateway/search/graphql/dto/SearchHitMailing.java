package com.github.dimitryivaniuta.gateway.search.graphql.dto;


import lombok.Builder;

@Builder
public record SearchHitMailing(String id, String title, String subtitle, float score, String mailingId) implements SearchHit {}
