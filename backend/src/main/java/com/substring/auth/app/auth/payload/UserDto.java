package com.substring.auth.app.auth.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.substring.auth.app.auth.entities.Provider;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
    private String policePost;
    private UUID id;
    private String email;
    private String name;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String image;
    @Builder.Default
    private boolean enable = true;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
    @Builder.Default
    private Provider provider = Provider.LOCAL;
    private RoleDto role;
    private Boolean supervisor;
}
