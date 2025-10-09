package com.github.dimitryivaniuta.gateway.events;

import java.time.Instant;
import lombok.Builder;

@Builder
record ContactUpdated(String tenantId, String contactId, String title, String subtitle,
                      boolean visible, long version, Instant occurredAt) implements ContactEvent {}