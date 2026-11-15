package com.copmap.team.dto;

import com.copmap.team.entity.TeamMembership;
import com.copmap.team.enums.TeamRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        UUID teamId,
        UUID officerId,
        String officerName,
        String designation,
        TeamRole teamRole,
        OffsetDateTime joinedAt
) {
    public static TeamMemberResponse from(TeamMembership tm) {
        String name = tm.getOfficer().getOfficerName();
        if (name == null || name.isBlank()) {
            name = tm.getOfficer().getDesignation();
        }
        return new TeamMemberResponse(
                tm.getId(),
                tm.getTeam().getId(),
                tm.getOfficer().getId(),
                name,
                tm.getOfficer().getDesignation(),
                tm.getTeamRole(),
                tm.getJoinedAt()
        );
    }
}
