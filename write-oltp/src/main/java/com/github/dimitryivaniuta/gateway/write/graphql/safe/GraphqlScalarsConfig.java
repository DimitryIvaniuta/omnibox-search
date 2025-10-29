package com.github.dimitryivaniuta.gateway.write.graphql.safe;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers custom GraphQL scalars (e.g. Long).
 * This does not use repositories or services and is safe for schema export.
 */
@Configuration
public class GraphqlScalarsConfig {

    @Bean
    public RuntimeWiringConfigurer scalarsConfigurer() {
        return wiring -> wiring.scalar(ExtendedScalars.GraphQLLong);
    }
}
