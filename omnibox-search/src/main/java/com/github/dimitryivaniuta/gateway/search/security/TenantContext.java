package com.github.dimitryivaniuta.gateway.search.security;

public record TenantContext(String tenantId, String userId) {}