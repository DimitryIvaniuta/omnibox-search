package com.github.dimitryivaniuta.gateway.write.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Contact {
    private UUID id;
    private String tenantId;
    private String fullName;
    private String email;
    private String phone;
    private String label;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}