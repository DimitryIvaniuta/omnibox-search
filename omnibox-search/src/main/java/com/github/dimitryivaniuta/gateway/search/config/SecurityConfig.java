package com.github.dimitryivaniuta.gateway.search.config;

import com.github.dimitryivaniuta.gateway.search.security.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/graphql"))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/graphiql/**", "/graphql").permitAll() // tighten in prod
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new TenantFilter(), AnonymousAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Property-free CORS: allow all origins to call /graphql with POST/OPTIONS.
     * Keep tight headers; add Authorization if you introduce auth later.
     * NOTE: For production, restrict allowedOriginPatterns to your domains.
     */
    @Bean
    public CorsFilter corsFilter() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("POST", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-Tenant"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/graphql", cfg);
        return new CorsFilter(source);
    }
}