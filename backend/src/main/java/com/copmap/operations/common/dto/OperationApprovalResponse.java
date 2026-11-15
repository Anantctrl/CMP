package com.copmap.operations.common.dto;

import com.copmap.operations.common.entity.OperationApprovalRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationApprovalResponse(
        UUID id,
        UUID operationId,
        String operationTitle,
        String operationType,
        UUID requestedById,
        UUID targetTeamId,
        String targetTeamName,
        String status,
        UUID reviewedById,
        OffsetDateTime reviewedAt,
        String requestRemarks,
        String reviewRemarks,
        OffsetDateTime createdAt
) {
    public static OperationApprovalResponse from(OperationApprovalRequest req) {
        return new OperationApprovalResponse(
                req.getId(),
                req.getOperation().getId(),
                req.getOperation().getTitle(),
                req.getOperation().getType().name(),
                req.getRequestedBy().getId(),
                req.getTargetTeam().getId(),
                req.getTargetTeam().getName(),
                req.getStatus(),
                req.getReviewedBy() != null ? req.getReviewedBy().getId() : null,
                req.getReviewedAt(),
                req.getRequestRemarks(),
                req.getReviewRemarks(),
                req.getCreatedAt()
        );
    }
}
