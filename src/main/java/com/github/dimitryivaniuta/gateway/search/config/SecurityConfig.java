package com.github.dimitryivaniuta.gateway.search.config;

import com.github.dimitryivaniuta.gateway.search.security.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;


@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/graphiql/**", "/graphql").permitAll() // tighten in prod
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new TenantFilter(), AnonymousAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}