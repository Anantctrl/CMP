package com.substring.auth.app.operations.common.dto;

import com.substring.auth.app.operations.common.entity.Operation;
import com.substring.auth.app.operations.common.enums.OperationStatus;
import com.substring.auth.app.operations.common.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        OperationType type,
        String title,
        String description,
        OffsetDateTime plannedStartAt,
        OffsetDateTime plannedEndAt,
        OffsetDateTime actualStartAt,
        OffsetDateTime actualEndAt,
        OperationStatus status,
        UUID createdByOfficerId,
        List<UUID> participatingTeamIds,
        boolean pendingApproval,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static OperationResponse from(Operation operation) {
        return from(operation, List.of(), false);
    }

    public static OperationResponse from(
            Operation operation,
            List<UUID> participatingTeamIds,
            boolean pendingApproval
    ) {
        return new OperationResponse(
                operation.getId(),
                operation.getType(),
                operation.getTitle(),
                operation.getDescription(),
                operation.getPlannedStartAt(),
                operation.getPlannedEndAt(),
                operation.getActualStartAt(),
                operation.getActualEndAt(),
                operation.getStatus(),
                operation.getCreatedBy().getId(),
                participatingTeamIds != null ? participatingTeamIds : List.of(),
                pendingApproval,
                operation.getCreatedAt(),
                operation.getUpdatedAt()
        );
    }
}
