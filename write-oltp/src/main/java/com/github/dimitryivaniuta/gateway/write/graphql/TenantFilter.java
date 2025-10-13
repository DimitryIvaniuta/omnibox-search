package com.github.dimitryivaniuta.gateway.write.graphql;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Extracts X-Tenant and stores it in a ThreadLocal for the request lifetime. */
public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String tenant = req.getHeader("X-Tenant");
            if (tenant != null && !tenant.isBlank()) {
                TenantContext.set(tenant.trim());
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
