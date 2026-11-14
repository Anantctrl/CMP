package com.substring.auth.app.officer.controller;

import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.service.OfficerDirectoryService;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.service.TeamAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/officers")
public class OfficerController {

    private final OfficerDirectoryService officerDirectoryService;
    private final TeamAuthorizationService authService;

    public OfficerController(
            OfficerDirectoryService officerDirectoryService,
            TeamAuthorizationService authService
    ) {
        this.officerDirectoryService = officerDirectoryService;
        this.authService = authService;
    }

    // Only Supervisors or Administrators can view the un-scoped division-wide roster
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<List<OfficerDirectoryDto>> getAllOfficers() {
        return ResponseEntity.ok(officerDirectoryService.getAllOfficersCached());
    }

    // Availability must be scoped to an authorized team role
    @GetMapping("/available")
    public ResponseEntity<List<OfficerDirectoryDto>> getAvailableOfficers(
            @RequestParam(required = false) UUID teamId
    ) {
        if (teamId != null) {
            authService.requireTeamRole(
                    teamId,
                    "You are not authorized to view available officers for this team",
                    TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.CONTROL_OPERATOR, TeamRole.ADMINISTRATOR
            );
            return ResponseEntity.ok(officerDirectoryService.getAvailableOfficersForTeam(teamId));
        }

        // Without teamId, requires division-level supervisor or admin authority
        authService.requireSupervisor("Only supervisors can view the full un-scoped directory of available officers");
        return ResponseEntity.ok(officerDirectoryService.getAllOfficersCached());
    }

    // Only Supervisors or Administrators can evict/clear the Redis directory cache
    @PostMapping("/cache/evict")
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<Void> evictCache() {
        officerDirectoryService.evictDirectoryCache();
        return ResponseEntity.noContent().build();
    }
}
