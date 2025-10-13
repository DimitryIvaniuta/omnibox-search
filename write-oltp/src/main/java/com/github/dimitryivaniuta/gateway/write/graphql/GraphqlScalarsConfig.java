package com.github.dimitryivaniuta.gateway.write.graphql;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers custom GraphQL scalars once per application.
 * Keep resolver wiring in a separate config to avoid duplicate registrations.
 */
@Configuration
public class GraphqlScalarsConfig {

    @Bean
    public RuntimeWiringConfigurer scalarsConfigurer() {
        return wiring -> wiring.scalar(ExtendedScalars.GraphQLLong);
    }
}
