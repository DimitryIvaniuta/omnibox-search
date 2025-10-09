package com.github.dimitryivaniuta.gateway.events;

import lombok.Builder;

import java.time.Instant;

@Builder
record ContactCreated(String tenantId, String contactId, String title, String subtitle,
                      boolean visible, long version, Instant occurredAt) implements ContactEvent {}