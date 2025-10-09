package com.github.dimitryivaniuta.gateway.search.config;

import graphql.GraphQLError;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.GraphqlErrorBuilder;
import graphql.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer; // use this one
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.server.WebGraphQlInterceptor;

import java.util.List;
import java.util.Map;

@Configuration
public class GraphqlConfig {

    /**
     * Interceptor that reads tenant from header and injects it into the
     * ExecutionInput "context" (legacy, portable across versions).
     */
    @Bean
    public WebGraphQlInterceptor tenantGraphQlInterceptor(
            @Value("${app.tenancy.header:X-Tenant}") String tenantHeader,
            @Value("${app.tenancy.allow-missing:true}") boolean allowMissingTenant
    ) {
        return (request, chain) -> {
            String tenant = request.getHeaders().getFirst(tenantHeader);
            if (tenant == null && allowMissingTenant) tenant = "";
            final String t = tenant; // effectively final

            // Put tenant into legacy context; resolvers will read env.getContext()
            request.configureExecutionInput((executionInput, builder) ->
                    builder.context(Map.of("tenantId", t == null ? "" : t)).build()
            );

            return chain.next(request);
        };
    }

    /** Depth/complexity limits using graphql-java instrumentation. */
    @Bean
    public GraphQlSourceBuilderCustomizer hardeningCustomizer() {
        return builder -> builder.configureGraphQl(graphQlBuilder -> {
            Instrumentation depth = new MaxQueryDepthInstrumentation(8);
            Instrumentation complexity = new MaxQueryComplexityInstrumentation(200);
            graphQlBuilder.instrumentation(new ChainedInstrumentation(List.of(depth, complexity)));
        });
    }

    /** Turn IllegalArgumentException into a clean GraphQL error. */
    @Bean
    public DataFetcherExceptionResolver clientErrorResolver() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
                if (ex instanceof IllegalArgumentException iae) {
                    return GraphqlErrorBuilder.newError(env)
                            .message(iae.getMessage())
                            .errorType(ErrorType.ValidationError)  // use an existing enum
                            .build();
                }
                return null;
            }
        };
    }
}
