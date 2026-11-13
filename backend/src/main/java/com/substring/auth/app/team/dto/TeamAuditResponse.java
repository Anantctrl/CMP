package com.substring.auth.app.team.dto;

import com.substring.auth.app.team.entity.TeamAuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TeamAuditResponse(
        UUID id,
        UUID teamId,
        UUID officerId,
        UUID changedById,
        String action,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        String remarks,
        OffsetDateTime createdAt
) {
    public static TeamAuditResponse from(TeamAuditLog log) {
        return new TeamAuditResponse(
                log.getId(),
                log.getTeam().getId(),
                log.getOfficer() != null ? log.getOfficer().getId() : null,
                log.getChangedBy().getId(),
                log.getAction(),
                log.getOldValues(),
                log.getNewValues(),
                log.getRemarks(),
                log.getCreatedAt()
        );
    }
}
