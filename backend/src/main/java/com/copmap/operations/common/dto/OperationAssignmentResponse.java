
package com.copmap.operations.common.dto;

import com.copmap.operations.common.entity.OperationAssignment;
import com.copmap.operations.common.enums.OperationAssignmentStatus;
import com.copmap.operations.common.enums.OperationStatus;
import com.copmap.operations.common.enums.OperationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationAssignmentResponse(
        UUID id,

        UUID operationId,
        String operationTitle,
        OperationType operationType,
        OperationStatus operationStatus,

        UUID locationId,
        String locationName,
        String address,
        Double latitude,
        Double longitude,

        UUID officerId,
        UUID assignedByOfficerId,

        String responsibility,
        String instructions,
        OperationAssignmentStatus status,

        OffsetDateTime assignedAt,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,

        String completionReport,
        OffsetDateTime createdAt
) {
    public static OperationAssignmentResponse from(
            OperationAssignment assignment
    ) {
        var location = assignment.getLocation();
        var operation = location.getOperation();

        return new OperationAssignmentResponse(
                assignment.getId(),

                operation.getId(),
                operation.getTitle(),
                operation.getType(),
                operation.getStatus(),

                location.getId(),
                location.getName(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),

                assignment.getOfficer().getId(),
                assignment.getAssignedBy().getId(),

                assignment.getResponsibility(),
                assignment.getInstructions(),
                assignment.getStatus(),

                assignment.getAssignedAt(),
                assignment.getAcknowledgedAt(),
                assignment.getStartedAt(),
                assignment.getCompletedAt(),

                assignment.getCompletionReport(),
                assignment.getCreatedAt()
        );
    }
}
