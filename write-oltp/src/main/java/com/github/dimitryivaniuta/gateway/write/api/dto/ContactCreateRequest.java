package com.github.dimitryivaniuta.gateway.write.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactCreateRequest {
    @NotBlank
    private String fullName;
    private String email;
    private String phone;
    private String label;
}