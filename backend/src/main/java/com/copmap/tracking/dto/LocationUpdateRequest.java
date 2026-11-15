package com.copmap.tracking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record LocationUpdateRequest(
        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        Double accuracyMeters,

        OffsetDateTime recordedAt
) {}
