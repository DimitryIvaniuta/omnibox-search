package com.github.dimitryivaniuta.gateway.write.api.dto;

import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactResponse {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String label;
    private long version;

    /**
     * Convert domain -> API
     */
    public static ContactResponse toResponse(Contact c) {
        return new ContactResponse(
                c.getId().toString(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getLabel(),
                c.getVersion()
        );
    }

}