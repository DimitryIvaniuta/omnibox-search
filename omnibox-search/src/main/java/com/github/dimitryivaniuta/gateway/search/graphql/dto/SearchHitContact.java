package com.github.dimitryivaniuta.gateway.search.graphql.dto;

import lombok.Builder;

@Builder
public record SearchHitContact(String id, String title, String subtitle, float score, String contactId) implements SearchHit {
}