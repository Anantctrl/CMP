
package com.substring.auth.app.operations.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOperationLocationRequest(

        @NotBlank(message = "Location name is required")
        @Size(max = 150, message = "Location name cannot exceed 150 characters")
        String name,

        String description,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must not exceed 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must not exceed 180")
        Double longitude,

        @NotNull(message = "Required manpower is required")
        @Min(value = 0, message = "Required manpower cannot be negative")
        Integer requiredManpower,

        @NotNull(message = "Sequence order is required")
        @Min(value = 0, message = "Sequence order cannot be negative")
        Integer sequenceOrder

) {}
