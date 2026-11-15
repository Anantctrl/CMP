
package com.copmap.officer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReassignOfficerRequest(

        @NotNull(message = "New officer ID is required")
        UUID officerId,

        @NotBlank(message = "Responsibility is required")
        @Size(max = 150)
        String responsibility,

        String instructions,

        @NotBlank(message = "Reassignment reason is required")
        @Size(max = 500)
        String reason
) {}
