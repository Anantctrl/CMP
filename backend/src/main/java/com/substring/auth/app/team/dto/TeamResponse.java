package com.substring.auth.app.team.dto;

import com.substring.auth.app.team.entity.Team;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String description,
        OffsetDateTime createdAt,
        long memberCount,
        String userRoleInTeam,
        long activeOperationsCount
) {
    public static TeamResponse from(
            Team team,
            long memberCount,
            String userRoleInTeam,
            long activeOperationsCount
    ) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedAt(),
                memberCount,
                userRoleInTeam,
                activeOperationsCount
        );
    }
}
