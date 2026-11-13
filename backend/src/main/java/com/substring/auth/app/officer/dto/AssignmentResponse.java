package com.substring.auth.app.officer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID taskId,
        UUID officerId,
        String officerName,
        String officerDesignation,
        String responsibility,
        String instructions,
        String status,
        OffsetDateTime assignedAt,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime completedAt,
        String completionReport,
        String reviewRemarks
) {}
