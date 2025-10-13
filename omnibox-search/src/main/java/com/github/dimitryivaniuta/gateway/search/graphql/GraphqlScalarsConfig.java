package com.github.dimitryivaniuta.gateway.search.graphql;

import graphql.scalars.ExtendedScalars;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphqlScalarsConfig {
    @Bean
    GraphQlSourceBuilderCustomizer addExtendedScalars() {
        return builder -> builder.configureRuntimeWiring(wiring ->
                wiring.scalar(ExtendedScalars.GraphQLLong)
        );
    }
}
