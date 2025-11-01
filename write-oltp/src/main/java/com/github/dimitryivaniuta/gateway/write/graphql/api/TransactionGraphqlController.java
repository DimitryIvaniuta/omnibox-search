package com.github.dimitryivaniuta.gateway.write.graphql.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.TransactionCreateRequest;
import com.github.dimitryivaniuta.gateway.write.api.dto.TransactionResponse;
import com.github.dimitryivaniuta.gateway.write.api.dto.TransactionUpdateRequest;
import com.github.dimitryivaniuta.gateway.write.domain.Transaction;
import com.github.dimitryivaniuta.gateway.write.domain.repo.TransactionRepo;
import com.github.dimitryivaniuta.gateway.write.graphql.TenantContext;
import com.github.dimitryivaniuta.gateway.write.service.TransactionService;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TransactionGraphqlController {

    private final TransactionService txs;
    private final TransactionRepo repo;

    @QueryMapping
    public Transaction transaction(@Argument UUID id) {
        String tenant = TenantContext.get();
        return repo.find(tenant, id).orElse(null);
    }

    @QueryMapping
    public List<Transaction> searchTransactions(@Argument String q,
                                                @Argument Integer first) {
        String tenant = TenantContext.get();
        int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
        return repo.searchByPrefix(tenant, q, limit);
    }

    @QueryMapping
    public List<TransactionResponse> transactions(
            @Argument @Min(0) Integer offset,
            @Argument @Min(1) @Max(200) Integer limit
    ) {
        final String tenant = TenantContext.get();
        final int off = offset == null ? 0 : offset;
        final int lim = limit == null ? 50 : limit;
        return txs.find(tenant, off, lim);
    }

    @QueryMapping
    public TransactionResponse transactionById(@Argument UUID id) {
        final String tenant = TenantContext.get();
        return txs.findOne(tenant, id);
    }

    @MutationMapping
    public Transaction createTransaction(@Argument("input") TransactionCreateRequest input) {
        String tenant = TenantContext.get();
        TransactionResponse resp = txs.create(tenant, input);
        Transaction t = repo.find(tenant, UUID.fromString(resp.getId())).orElse(null);
        return t;
    }

    @MutationMapping
    public Transaction updateTransaction(@Argument UUID id,
                                         @Argument("input") TransactionUpdateRequest input) {
        String tenant = TenantContext.get();
        TransactionResponse r = txs.update(tenant, id, input);
        return repo.find(tenant, UUID.fromString(r.getId())).orElse(null);
    }

    @MutationMapping
    public Boolean deleteTransaction(@Argument UUID id,
                                     @Argument Long version) {
        String tenant = TenantContext.get();
        txs.delete(tenant, id, version);
        return Boolean.TRUE;
    }

    @MutationMapping
    public Boolean deleteTransactions(@Argument("ids") @NotEmpty List<UUID> ids) {
        final String tenant = TenantContext.get();
        txs.deleteBulk(tenant, ids);
        return Boolean.TRUE;
    }
}
