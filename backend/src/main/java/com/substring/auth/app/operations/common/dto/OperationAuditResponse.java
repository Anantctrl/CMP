package com.substring.auth.app.operations.common.dto;

import com.substring.auth.app.operations.common.entity.OperationAuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record OperationAuditResponse(

        UUID id,
        UUID operationId,
        UUID locationId,
        UUID assignmentId,
        UUID changedByOfficerId,

        String action,

        Map<String, Object> oldValues,
        Map<String, Object> newValues,

        String remarks,
        OffsetDateTime createdAt

) {

    public static OperationAuditResponse from(OperationAuditLog audit) {

        return new OperationAuditResponse(
                audit.getId(),
                audit.getOperation().getId(),

                audit.getLocation() != null
                        ? audit.getLocation().getId()
                        : null,

                audit.getAssignment() != null
                        ? audit.getAssignment().getId()
                        : null,

                audit.getChangedBy().getId(),

                audit.getAction(),
                audit.getOldValues(),
                audit.getNewValues(),
                audit.getRemarks(),
                audit.getCreatedAt()
        );
    }
}
