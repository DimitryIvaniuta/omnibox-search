package com.github.dimitryivaniuta.gateway.search.graphql;

import com.github.dimitryivaniuta.gateway.search.graphql.dto.OmniboxResult;
import com.github.dimitryivaniuta.gateway.search.service.OmniboxService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class QueryResolver {

    private final OmniboxService service;

    @QueryMapping
    public OmniboxResult omnibox(@Argument @NotBlank String q,
                                 @Argument(name = "limitPerGroup") @Min(1) @Max(20) Integer limit) {
        int l = (limit == null ? 5 : limit);
        return service.search(q, l);
    }

}