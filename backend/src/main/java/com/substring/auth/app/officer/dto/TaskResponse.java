package com.substring.auth.app.officer.dto;

import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID teamId,
        String title,
        String actionType,
        String description,
        String locationName,
        Double latitude,
        Double longitude,
        UUID createdBy,
        String status,
        List<String> assignedOfficerNames
) {}
