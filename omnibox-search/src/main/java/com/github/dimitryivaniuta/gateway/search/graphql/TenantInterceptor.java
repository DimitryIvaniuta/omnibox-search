package com.github.dimitryivaniuta.gateway.search.graphql;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TenantInterceptor implements WebGraphQlInterceptor {
    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String tenant = request.getHeaders().getFirst("X-Tenant");
        if (tenant != null && !tenant.isBlank()) {
            request.configureExecutionInput((exec, b) ->
                    b.graphQLContext(exec.getGraphQLContext().put("tenantId", tenant)).build()
            );
        }
        return chain.next(request);
    }
}
