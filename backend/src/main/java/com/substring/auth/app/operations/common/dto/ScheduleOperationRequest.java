
package com.substring.auth.app.operations.common.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ScheduleOperationRequest(

        @NotNull(message = "Planned start time is required")
        OffsetDateTime plannedStartAt,

        @NotNull(message = "Planned end time is required")
        OffsetDateTime plannedEndAt,

        String remarks

) {}

