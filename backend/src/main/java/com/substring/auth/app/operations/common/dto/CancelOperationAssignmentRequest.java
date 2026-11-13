package com.substring.auth.app.operations.common.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOperationAssignmentRequest(

        @NotBlank(message = "Cancellation reason is required")
        String reason

) {}