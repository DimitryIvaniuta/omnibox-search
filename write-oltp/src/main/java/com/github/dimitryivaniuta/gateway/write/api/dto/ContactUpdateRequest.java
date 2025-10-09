package com.github.dimitryivaniuta.gateway.write.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactUpdateRequest {
    @NotBlank
    private String fullName;
    private String email;
    private String phone;
    private String label;

    /** optimistic locking guard (current entity version) */
    @NotNull
    private Long version;
}