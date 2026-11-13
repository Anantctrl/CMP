package com.substring.auth.app.operations.common.dto;

import com.substring.auth.app.operations.common.enums.OperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateOperationRequest(
        @NotNull(message = "Operation type is required")
        OperationType type,

        @NotBlank(message = "Operation title is required")
        @Size(max = 150, message = "Operation title cannot exceed 150 characters")
        String title,

        String description,

        Set<UUID> teamIds,

        String requestRemarks
) {}
