package com.github.dimitryivaniuta.gateway.write.graphql;

public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String get() {
        String t = TENANT.get();
        if (t == null || t.isBlank()) throw new IllegalArgumentException("Missing X-Tenant header");
        return t;
    }

    public static void clear() {
        TENANT.remove();
    }
}
