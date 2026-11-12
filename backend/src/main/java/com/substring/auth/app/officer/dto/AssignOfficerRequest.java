package com.substring.auth.app.officer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignOfficerRequest(

        @NotNull(message = "Officer ID is required")
        UUID officerId,

        @NotBlank(message = "Responsibility is required")
        @Size(max = 150)
        String responsibility,

        String instructions
) {}
