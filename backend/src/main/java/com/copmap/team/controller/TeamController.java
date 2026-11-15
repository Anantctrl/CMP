package com.copmap.team.controller;

import com.copmap.team.dto.*;
import com.copmap.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        TeamResponse response = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAuthorizedTeams() {
        return ResponseEntity.ok(teamService.getAuthorizedTeams());
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeamDetail(
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamService.getTeamDetail(teamId));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamService.getTeamMembers(teamId));
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberResponse> addMember(
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.addMember(teamId, request));
    }

    @DeleteMapping("/{teamId}/members/{officerId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID teamId,
            @PathVariable UUID officerId,
            @RequestParam(required = false) String remarks
    ) {
        teamService.removeMember(teamId, officerId, remarks);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/audit")
    public ResponseEntity<List<TeamAuditResponse>> getTeamAudit(
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamService.getTeamAuditHistory(teamId));
    }
}
