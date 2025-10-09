package com.github.dimitryivaniuta.gateway.write.api.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactResponse {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String label;
    private long version;
}