package com.copmap.operations.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignOperationOfficerRequest(

        @NotNull(message = "Officer ID is required")
        UUID officerId,

        @NotBlank(message = "Responsibility is required")
        @Size(max = 150, message = "Responsibility cannot exceed 150 characters")
        String responsibility,

        String instructions

) {}