package com.github.dimitryivaniuta.gateway.search.graphql.safe;

import graphql.scalars.ExtendedScalars;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the GraphQL Long scalar for omnibox-search.
 * Loaded by both runtime app and schema-export app.
 * Do NOT register the same scalar elsewhere to avoid duplicates.
 */
@Configuration
public class GraphqlSafeConfig {

    @Bean
    GraphQlSourceBuilderCustomizer addExtendedScalars() {
        return builder -> builder.configureRuntimeWiring(wiring ->
                wiring.scalar(ExtendedScalars.GraphQLLong)
        );
    }
}
