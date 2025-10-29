package com.github.dimitryivaniuta.gateway.write.config;

import com.github.dimitryivaniuta.gateway.write.graphql.TenantFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // allow CORS using the CorsConfigurationSource bean below
                .cors(Customizer.withDefaults())

                // disable/ignore CSRF for API & GraphQL (no auth/session forms here)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/graphql", "/api/**")
                )

                // authorization rules (no auth for now)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/graphql").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll()
                )

                // keep your tenant header propagation
                .addFilterBefore(new TenantFilter(), AnonymousAuthenticationFilter.class)

                // basic disabled or left default; harmless
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * CORS for GraphQL and REST API
     * Allow common methods and headers used by your frontend.
     * Tighten allowed origins in prod.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*")); // TODO: restrict in prod
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type","X-Tenant","Authorization"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/graphql", cfg);
        source.registerCorsConfiguration("/api/**", cfg);   // add API
        source.registerCorsConfiguration("/**", cfg);       // optional catch-all
        return source;
    }
}
