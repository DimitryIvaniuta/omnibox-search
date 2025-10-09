package com.github.dimitryivaniuta.gateway.search.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            String tenant = null;
            String user = null;
            if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
// In production use NimbusJwtDecoder and SecurityConfig to parse & verify JWT.
// Here we accept X-Tenant header as a fallback for local dev.
                tenant = request.getHeader("X-Tenant");
                user = request.getHeader("X-User");
            }
            if (!StringUtils.hasText(tenant)) tenant = "demo"; // replace in prod
            TenantContextHolder.set(new TenantContext(tenant, user));
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}