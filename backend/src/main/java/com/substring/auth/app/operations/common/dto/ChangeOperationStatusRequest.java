package com.substring.auth.app.operations.common.dto;

import com.substring.auth.app.operations.common.enums.OperationStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeOperationStatusRequest(

        @NotNull(message = "Operation status is required")
        OperationStatus status,

        String remarks

) {}
