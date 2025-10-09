package com.github.dimitryivaniuta.gateway.events;

import java.time.Instant;

import lombok.Builder;

public sealed interface ContactEvent permits ContactCreated, ContactUpdated, ContactDeleted {
    String tenantId();

    String contactId();

    Instant occurredAt();

    long version();

    boolean visible();

    default String key() {
        return tenantId() + ":" + contactId();
    }
}