package com.github.dimitryivaniuta.gateway.write.graphql.api;

import com.github.dimitryivaniuta.gateway.write.api.dto.ContactCreateRequest;
import com.github.dimitryivaniuta.gateway.write.api.dto.ContactResponse;
import com.github.dimitryivaniuta.gateway.write.api.dto.ContactUpdateRequest;
import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import com.github.dimitryivaniuta.gateway.write.domain.repo.ContactRepo;
import com.github.dimitryivaniuta.gateway.write.graphql.TenantContext;
import com.github.dimitryivaniuta.gateway.write.service.ContactService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

@Controller
@RequiredArgsConstructor
public class ContactGraphqlController {

    private final ContactService contacts;
    private final ContactRepo repo;

    @QueryMapping
    public Contact contact(@Argument UUID id) {
        String tenant = TenantContext.get();
        return repo.find(tenant, id).orElse(null);
    }

    @QueryMapping
    public List<Contact> searchContacts(@Argument String q,
                                        @Argument Integer first) {
        String tenant = TenantContext.get();
        int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
        return repo.searchByPrefix(tenant, q, limit);
    }

    @QueryMapping
    public List<ContactResponse> contacts(@Argument @Min(0) Integer offset,
                                     @Argument @Min(1) @Max(200) Integer limit) {
        String tenant = TenantContext.get();
        int off = offset == null ? 0 : offset;
        int lim = limit == null ? 50 : limit;
        return contacts.find(tenant, off, lim);
    }

    /**
     * One by id. Returns null if not found.
     */
    @QueryMapping
    public ContactResponse contactById(@Argument UUID id) {
        String tenant = TenantContext.get();
        return contacts.findOne(tenant, id);
    }

    @MutationMapping
    public Contact createContact(@Argument("input") ContactCreateRequest input) {
        String tenant = TenantContext.get();
        ContactResponse r = contacts.create(tenant, input);
        return repo.find(tenant, UUID.fromString(r.getId())).orElse(null);
    }

    @MutationMapping
    public Contact updateContact(@Argument UUID id,
                                 @Argument("input") ContactUpdateRequest input) {
        String tenant = TenantContext.get();
        ContactResponse r = contacts.update(tenant, id, input);
        return repo.find(tenant, UUID.fromString(r.getId())).orElse(null);
    }

    @MutationMapping
    public Boolean deleteContact(@Argument UUID id,
                                 @Argument Long version) {
        String tenant = TenantContext.get();
        contacts.delete(tenant, id, version);
        return Boolean.TRUE;
    }

    @MutationMapping
    public Boolean deleteContacts(@Argument("ids") @NotEmpty List<UUID> ids) {
        String tenant = TenantContext.get();
        contacts.deleteBulk(tenant, ids);
        return Boolean.TRUE;
    }

}
