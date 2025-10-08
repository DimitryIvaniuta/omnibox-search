package com.github.dimitryivaniuta.gateway.search.graphql.dto;

public interface SearchHit {
    String id();
    String title();
    String subtitle();
    float  score();
}