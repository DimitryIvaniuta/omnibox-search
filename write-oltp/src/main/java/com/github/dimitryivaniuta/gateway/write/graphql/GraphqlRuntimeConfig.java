package com.github.dimitryivaniuta.gateway.write.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.write.api.dto.*;
import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;
import com.github.dimitryivaniuta.gateway.write.domain.Transaction;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ContactRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ListingRepo;
import com.github.dimitryivaniuta.gateway.write.domain.repo.TransactionRepo;
import com.github.dimitryivaniuta.gateway.write.service.ContactService;
import com.github.dimitryivaniuta.gateway.write.service.ListingService;
import com.github.dimitryivaniuta.gateway.write.service.TransactionService;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Central GraphQL wiring: scalars + data fetchers for Query/Mutation.
 * Avoids per-method @QueryMapping/@MutationMapping.
 */
@Configuration
public class GraphqlRuntimeConfig {

    private final ObjectMapper om;
    private final ContactService contactService;
    private final ListingService listingService;
    private final TransactionService transactionService;
    private final ContactRepo contactRepo;
    private final ListingRepo listingRepo;
    private final TransactionRepo transactionRepo;

    public GraphqlRuntimeConfig(
            ObjectMapper om,
            ContactService contactService,
            ListingService listingService,
            TransactionService transactionService,
            ContactRepo contactRepo,
            ListingRepo listingRepo,
            TransactionRepo transactionRepo) {
        this.om = om;
        this.contactService = contactService;
        this.listingService = listingService;
        this.transactionService = transactionService;
        this.contactRepo = contactRepo;
        this.listingRepo = listingRepo;
        this.transactionRepo = transactionRepo;
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring
                // Scalars
                .scalar(ExtendedScalars.GraphQLLong)
                // -------- Query --------
                .type("Query", type -> type
                        .dataFetcher("contact", contactById())
                        .dataFetcher("searchContacts", searchContacts())
                        .dataFetcher("listing", listingById())
                        .dataFetcher("searchListings", searchListings())
                        .dataFetcher("transaction", transactionById())
                        .dataFetcher("searchTransactions", searchTransactions())
                )
                // -------- Mutation --------
                .type("Mutation", type -> type
                        .dataFetcher("createContact", createContact())
                        .dataFetcher("updateContact", updateContact())
                        .dataFetcher("deleteContact", deleteContact())
                        .dataFetcher("createListing", createListing())
                        .dataFetcher("updateListing", updateListing())
                        .dataFetcher("deleteListing", deleteListing())
                        .dataFetcher("createTransaction", createTransaction())
                        .dataFetcher("updateTransaction", updateTransaction())
                        .dataFetcher("deleteTransaction", deleteTransaction())
                );
    }

    // ================= Query fetchers =================

    private DataFetcher<Contact> contactById() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            return contactRepo.find(tenant, id).orElse(null);
        };
    }

    private DataFetcher<List<Contact>> searchContacts() {
        return env -> {
            String tenant = requireTenant();
            String q = env.getArgument("q");
            Integer first = env.getArgument("first");
            int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
            return contactRepo.searchByPrefix(tenant, q, limit);
        };
    }

    private DataFetcher<Listing> listingById() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            return listingRepo.find(tenant, id).orElse(null);
        };
    }

    private DataFetcher<List<Listing>> searchListings() {
        return env -> {
            String tenant = requireTenant();
            String q = env.getArgument("q");
            Integer first = env.getArgument("first");
            int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
            return listingRepo.searchByPrefix(tenant, q, limit);
        };
    }

    private DataFetcher<Transaction> transactionById() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            return transactionRepo.find(tenant, id).orElse(null);
        };
    }

    private DataFetcher<List<Transaction>> searchTransactions() {
        return env -> {
            String tenant = requireTenant();
            String q = env.getArgument("q");
            Integer first = env.getArgument("first");
            int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
            return transactionRepo.searchByPrefix(tenant, q, limit);
        };
    }

    // ================= Mutation fetchers =================

    private DataFetcher<Contact> createContact() {
        return env -> {
            String tenant = requireTenant();
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, ContactCreateRequest.class);
            var res = contactService.create(tenant, req);
            return toContactDomain(res);
        };
    }

    private DataFetcher<Contact> updateContact() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, ContactUpdateRequest.class);
            var res = contactService.update(tenant, id, req);
            return toContactDomain(res);
        };
    }

    private DataFetcher<Boolean> deleteContact() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Long version = env.getArgument("version");
            contactService.delete(tenant, id, version);
            return Boolean.TRUE;
        };
    }

    private DataFetcher<Listing> createListing() {
        return env -> {
            String tenant = requireTenant();
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, com.github.dimitryivaniuta.gateway.write.api.dto.ListingCreateRequest.class);
            var res = listingService.create(tenant, req);
            return listingRepo.find(tenant, UUID.fromString(res.getId())).orElse(null);
        };
    }

    private DataFetcher<Listing> updateListing() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, com.github.dimitryivaniuta.gateway.write.api.dto.ListingUpdateRequest.class);
            var res = listingService.update(tenant, id, req);
            return listingRepo.find(tenant, UUID.fromString(res.getId())).orElse(null);
        };
    }

    private DataFetcher<Boolean> deleteListing() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Long version = env.getArgument("version");
            listingService.delete(tenant, id, version);
            return Boolean.TRUE;
        };
    }

    private DataFetcher<Transaction> createTransaction() {
        return env -> {
            String tenant = requireTenant();
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, TransactionCreateRequest.class);
            var res = transactionService.create(tenant, req);
            return transactionRepo.find(tenant, UUID.fromString(res.getId())).orElse(null);
        };
    }

    private DataFetcher<Transaction> updateTransaction() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Map<String, Object> input = env.getArgument("input");
            var req = om.convertValue(input, TransactionUpdateRequest.class);
            var res = transactionService.update(tenant, id, req);
            return transactionRepo.find(tenant, UUID.fromString(res.getId())).orElse(null);
        };
    }

    private DataFetcher<Boolean> deleteTransaction() {
        return env -> {
            String tenant = requireTenant();
            UUID id = UUID.fromString(env.getArgument("id"));
            Long version = env.getArgument("version");
            transactionService.delete(tenant, id, version);
            return Boolean.TRUE;
        };
    }

    // ================= Helpers =================

    private static String requireTenant() {
        String t = TenantContext.get();
        if (t == null || t.isBlank()) {
            throw new IllegalArgumentException("Missing X-Tenant header");
        }
        return t;
    }

    private static Contact toContactDomain(ContactResponse r) {
        return Contact.builder()
                .id(UUID.fromString(r.getId()))
                .fullName(r.getFullName())
                .email(r.getEmail())
                .phone(r.getPhone())
                .label(r.getLabel())
                .version(r.getVersion())
                .build();
    }
}
