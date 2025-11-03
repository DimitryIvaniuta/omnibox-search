package com.github.dimitryivaniuta.gateway.search.security;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CTX = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext ctx) {
        CTX.set(ctx);
    }

    public static TenantContext get() {
        return CTX.get();
    }

    public static String getRequiredTenant() {
        TenantContext ctx = CTX.get();
        if (ctx == null || ctx.tenantId() == null) throw new IllegalStateException("Tenant is required");
        return ctx.tenantId();
    }

    public static void clear() {
        CTX.remove();
    }
}