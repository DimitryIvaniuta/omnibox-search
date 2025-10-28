package com.github.dimitryivaniuta.gateway.search.config;

import com.github.dimitryivaniuta.gateway.search.lb.InMemoryLoadBalancer;
import com.github.dimitryivaniuta.gateway.search.lb.LoadBalancer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration.
 *
 * Drop this library on the classpath of any Spring Boot app and you'll get
 * a singleton {@link LoadBalancer} bean unless the app defines its own.
 */
@Configuration
public class LoadBalancerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoadBalancer.class)
    public LoadBalancer loadBalancer() {
        return new InMemoryLoadBalancer();
    }

}
