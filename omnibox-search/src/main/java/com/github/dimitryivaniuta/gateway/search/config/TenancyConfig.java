package com.github.dimitryivaniuta.gateway.search.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class TenancyConfig {

    @Value("${app.tenancy.header:X-Tenant}")
    private String tenantHeader;

    @Value("${app.tenancy.allow-missing:true}") // set false in prod
    private boolean allowMissingTenant;

    public static final String TENANT_REQUEST_ATTR = "tenantId";

    @Bean
    public OncePerRequestFilter tenantFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String tenant = request.getHeader(tenantHeader);
                if (!StringUtils.hasText(tenant) && !allowMissingTenant) {
                    response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing tenant header: " + tenantHeader);
                    return;
                }
                request.setAttribute(TENANT_REQUEST_ATTR, tenant == null ? "" : tenant);
                try { chain.doFilter(request, response); }
                finally { request.removeAttribute(TENANT_REQUEST_ATTR); }
            }
        };
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${app.cors.enabled:true}") boolean corsEnabled,
            @Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (!corsEnabled) return;
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS");
            }
        };
    }
}
