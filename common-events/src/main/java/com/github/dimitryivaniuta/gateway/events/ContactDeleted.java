package com.github.dimitryivaniuta.gateway.events;

import java.time.Instant;
import lombok.Builder;

@Builder
record ContactDeleted(String tenantId, String contactId, boolean visible,
                      long version, Instant occurredAt) implements ContactEvent {}