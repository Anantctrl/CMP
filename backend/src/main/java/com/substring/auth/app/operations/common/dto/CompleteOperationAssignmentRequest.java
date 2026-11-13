package com.substring.auth.app.operations.common.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteOperationAssignmentRequest(

        @NotBlank(message = "Completion report is required")
        String completionReport

) {}