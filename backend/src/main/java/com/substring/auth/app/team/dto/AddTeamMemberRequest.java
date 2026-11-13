package com.substring.auth.app.team.dto;

import com.substring.auth.app.team.enums.TeamRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull(message = "Officer ID is required")
        UUID officerId,

        @NotNull(message = "Team role is required")
        TeamRole teamRole
) {}
