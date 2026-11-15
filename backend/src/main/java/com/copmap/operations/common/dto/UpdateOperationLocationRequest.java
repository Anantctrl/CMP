
package com.copmap.operations.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOperationLocationRequest(

        @NotBlank(message = "Location name is required")
        @Size(max = 150, message = "Location name cannot exceed 150 characters")
        String name,

        String description,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,

        @NotNull(message = "Required manpower is required")
        @Min(value = 0, message = "Required manpower cannot be negative")
        Integer requiredManpower,

        @NotNull(message = "Sequence order is required")
        @Min(value = 0, message = "Sequence order cannot be negative")
        Integer sequenceOrder

) {}
