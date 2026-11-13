package com.substring.auth.app.tracking.service;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.service.CurrentOfficerService;
import com.substring.auth.app.team.entity.TeamMembership;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.repository.TeamMembershipRepository;
import com.substring.auth.app.team.repository.TeamRepository;
import com.substring.auth.app.team.service.TeamAuthorizationService;
import com.substring.auth.app.tracking.dto.LocationUpdateRequest;
import com.substring.auth.app.tracking.dto.OfficerLiveLocationResponse;
import com.substring.auth.app.tracking.entity.OfficerLocationUpdate;
import com.substring.auth.app.tracking.repository.OfficerLocationUpdateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LocationTrackingService {

    private final OfficerLocationUpdateRepository locationRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TeamMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final TeamAuthorizationService authService;
    private final OfficerProfileRepository officerRepository;

    public LocationTrackingService(
            OfficerLocationUpdateRepository locationRepository,
            CurrentOfficerService currentOfficerService,
            TeamMembershipRepository membershipRepository,
            TeamRepository teamRepository,
            TeamAuthorizationService authService,
            OfficerProfileRepository officerRepository
    ) {
        this.locationRepository = locationRepository;
        this.currentOfficerService = currentOfficerService;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.authService = authService;
        this.officerRepository = officerRepository;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OfficerLiveLocationResponse recordLocation(LocationUpdateRequest request) {
        if (request.latitude() == null || request.latitude() < -90 || request.latitude() > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }

        if (request.longitude() == null || request.longitude() < -180 || request.longitude() > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }

        if (request.accuracyMeters() != null && request.accuracyMeters() < 0) {
            throw new IllegalArgumentException("Accuracy cannot be negative");
        }

        OffsetDateTime recordedAt = request.recordedAt() != null
                ? request.recordedAt()
                : OffsetDateTime.now();

        // Never trust client-supplied officerId; resolve securely from authenticated token
        OfficerProfile currentOfficer = currentOfficerService.getCurrentOfficer();

        OfficerLocationUpdate update = new OfficerLocationUpdate();
        update.setOfficer(currentOfficer);
        update.setLatitude(request.latitude());
        update.setLongitude(request.longitude());
        update.setAccuracyMeters(request.accuracyMeters());
        update.setRecordedAt(recordedAt);
        update.setReceivedAt(OffsetDateTime.now());

        OfficerLocationUpdate saved = locationRepository.save(update);
        return OfficerLiveLocationResponse.from(saved);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<OfficerLiveLocationResponse> getLiveLocationsForTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new EntityNotFoundException("Team not found");
        }

        // Control Operators, Team Leads, Senior Planners, and Supervisors can monitor team live map
        authService.requireTeamRole(
                teamId,
                "You are not authorized to view live locations for this team",
                TeamRole.CONTROL_OPERATOR, TeamRole.TEAM_LEAD, TeamRole.SENIOR_PLANNER, TeamRole.ADMINISTRATOR
        );

        List<TeamMembership> members = membershipRepository.findAllByTeam_Id(teamId);
        List<OfficerLiveLocationResponse> results = new ArrayList<>();

        for (TeamMembership member : members) {
            locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc(member.getOfficer().getId())
                    .ifPresent(loc -> results.add(OfficerLiveLocationResponse.from(loc)));
        }

        return results;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public Optional<OfficerLiveLocationResponse> getOfficerLatestLocation(UUID officerId) {
        OfficerProfile targetOfficer = officerRepository.findById(officerId)
                .orElseThrow(() -> new EntityNotFoundException("Officer not found"));

        OfficerProfile currentOfficer = currentOfficerService.getCurrentOfficer();

        // If requesting own location or supervisor
        if (targetOfficer.getId().equals(currentOfficer.getId()) || authService.isSupervisor()) {
            return locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc(officerId)
                    .map(OfficerLiveLocationResponse::from);
        }

        // Otherwise verify caller shares a team where caller has monitoring role
        List<UUID> callerTeams = membershipRepository.findTeamIdsByOfficerId(currentOfficer.getId());
        List<UUID> targetTeams = membershipRepository.findTeamIdsByOfficerId(targetOfficer.getId());

        boolean sharedMonitoredTeam = callerTeams.stream()
                .filter(targetTeams::contains)
                .anyMatch(teamId -> authService.hasTeamRole(
                        teamId,
                        TeamRole.CONTROL_OPERATOR, TeamRole.TEAM_LEAD, TeamRole.SENIOR_PLANNER
                ));

        if (!sharedMonitoredTeam) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not authorized to view this officer's location"
            );
        }

        return locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc(officerId)
                .map(OfficerLiveLocationResponse::from);
    }
}
