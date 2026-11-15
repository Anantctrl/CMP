package com.copmap.officer.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitAssignmentRequest(
        @NotBlank(message = "Completion report is required")
        String completionReport
) {}
