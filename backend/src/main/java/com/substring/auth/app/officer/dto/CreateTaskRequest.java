package com.substring.auth.app.officer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(

        @NotBlank(message = "Task title is required")
        @Size(max = 150)
        String title,

        @NotBlank(message = "Action type is required")
        String actionType,

        String description,

        String locationName,

        Double latitude,

        Double longitude
) {}
