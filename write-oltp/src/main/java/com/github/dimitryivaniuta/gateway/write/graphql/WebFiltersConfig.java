package com.github.dimitryivaniuta.gateway.write.graphql;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers TenantFilter for all requests (incl. /graphql).
 */
@Configuration
public class WebFiltersConfig {
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration() {
        var r = new FilterRegistrationBean<>(new TenantFilter());
        r.setOrder(0);                // early
        r.addUrlPatterns("/*");
        return r;
    }
}
