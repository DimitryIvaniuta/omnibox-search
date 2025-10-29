package com.github.dimitryivaniuta.gateway.search.config;

import graphql.GraphQLError;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import graphql.GraphqlErrorBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
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
     * Reads tenant from HTTP header and injects it into ExecutionInput legacy context (portable).
     * Resolvers can read it via env.getContext().
     */
    @Bean
    public WebGraphQlInterceptor tenantGraphQlInterceptor(
            @Value("${app.tenancy.header:X-Tenant}") String tenantHeader,
            @Value("${app.tenancy.allow-missing:true}") boolean allowMissingTenant
    ) {
        return (request, chain) -> {
            String tenant = request.getHeaders().getFirst(tenantHeader);
            if (tenant == null && allowMissingTenant) tenant = "";
            final String t = tenant;

            // NOTE: context(Object) is deprecated in newer graphql-java, but is the most
            // version-compatible option. It's safe to use; suppress the warning at compile time.
            request.configureExecutionInput((executionInput, builder) ->
                    builder
                            //noinspection removal
                            .context(Map.of("tenantId", t == null ? "" : t))
                            .build()
            );

            return chain.next(request);
        };
    }

    /** Depth/complexity limits using graphql-java instrumentation (no custom overrides). */
    @Bean
    public GraphQlSourceBuilderCustomizer hardeningCustomizer() {
        return builder -> builder.configureGraphQl(graphQlBuilder -> {
            Instrumentation depth = new MaxQueryDepthInstrumentation(32);
            Instrumentation complexity = new MaxQueryComplexityInstrumentation(200);
            graphQlBuilder.instrumentation(new ChainedInstrumentation(List.of(depth, complexity)));
        });
    }

    /** Map IllegalArgumentException to a neat GraphQL error (portable across versions). */
    @Bean
    public DataFetcherExceptionResolver clientErrorResolver() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
                if (ex instanceof IllegalArgumentException iae) {
                    return GraphqlErrorBuilder.newError(env)
                            .message(iae.getMessage())
                            .build();
                }
                return null;
            }
        };
    }
}
