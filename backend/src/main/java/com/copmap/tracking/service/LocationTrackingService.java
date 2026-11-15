package com.copmap.tracking.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.repository.OfficerProfileRepository;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.team.entity.TeamMembership;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamMembershipRepository;
import com.copmap.team.repository.TeamRepository;
import com.copmap.team.service.TeamAuthorizationService;
import com.copmap.tracking.dto.LocationUpdateRequest;
import com.copmap.tracking.dto.OfficerLiveLocationResponse;
import com.copmap.tracking.entity.OfficerLocationUpdate;
import com.copmap.tracking.repository.OfficerLocationUpdateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LocationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LocationTrackingService.class);
    private static final String LIVE_LOCATION_REDIS_KEY_PREFIX = "officer:live:";

    private final OfficerLocationUpdateRepository locationRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TeamMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final TeamAuthorizationService authService;
    private final OfficerProfileRepository officerRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public LocationTrackingService(
            OfficerLocationUpdateRepository locationRepository,
            CurrentOfficerService currentOfficerService,
            TeamMembershipRepository membershipRepository,
            TeamRepository teamRepository,
            TeamAuthorizationService authService,
            OfficerProfileRepository officerRepository,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.locationRepository = locationRepository;
        this.currentOfficerService = currentOfficerService;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.authService = authService;
        this.officerRepository = officerRepository;
        this.redisTemplate = redisTemplate;
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
        OfficerLiveLocationResponse response = OfficerLiveLocationResponse.from(saved);

        try {
            redisTemplate.opsForValue().set(
                    LIVE_LOCATION_REDIS_KEY_PREFIX + currentOfficer.getId(),
                    response,
                    Duration.ofHours(24)
            );
        } catch (Exception e) {
            log.warn("Failed to cache live location in Redis: {}", e.getMessage());
        }

        return response;
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
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> memberOfficerIds = members.stream()
                .map(m -> m.getOfficer().getId())
                .toList();

        List<OfficerLiveLocationResponse> results = new ArrayList<>();
        List<UUID> missingFromCache = new ArrayList<>();

        for (UUID officerId : memberOfficerIds) {
            try {
                Object cached = redisTemplate.opsForValue().get(LIVE_LOCATION_REDIS_KEY_PREFIX + officerId);
                if (cached instanceof OfficerLiveLocationResponse loc) {
                    results.add(loc);
                } else {
                    missingFromCache.add(officerId);
                }
            } catch (Exception e) {
                missingFromCache.add(officerId);
            }
        }

        // Batch fetch all missing officers in a single query (N+1 elimination)
        if (!missingFromCache.isEmpty()) {
            List<OfficerLocationUpdate> dbUpdates = locationRepository.findLatestLocationsForOfficers(missingFromCache);
            for (OfficerLocationUpdate update : dbUpdates) {
                OfficerLiveLocationResponse resp = OfficerLiveLocationResponse.from(update);
                results.add(resp);
                try {
                    redisTemplate.opsForValue().set(
                            LIVE_LOCATION_REDIS_KEY_PREFIX + update.getOfficer().getId(),
                            resp,
                            Duration.ofHours(24)
                    );
                } catch (Exception ignored) {
                }
            }
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
            return fetchLatestFromCacheOrDb(officerId);
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

        return fetchLatestFromCacheOrDb(officerId);
    }

    private Optional<OfficerLiveLocationResponse> fetchLatestFromCacheOrDb(UUID officerId) {
        try {
            Object cached = redisTemplate.opsForValue().get(LIVE_LOCATION_REDIS_KEY_PREFIX + officerId);
            if (cached instanceof OfficerLiveLocationResponse loc) {
                return Optional.of(loc);
            }
        } catch (Exception e) {
            log.warn("Failed to read officer live location from Redis: {}", e.getMessage());
        }

        return locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc(officerId)
                .map(loc -> {
                    OfficerLiveLocationResponse resp = OfficerLiveLocationResponse.from(loc);
                    try {
                        redisTemplate.opsForValue().set(
                                LIVE_LOCATION_REDIS_KEY_PREFIX + officerId,
                                resp,
                                Duration.ofHours(24)
                        );
                    } catch (Exception ignored) {
                    }
                    return resp;
                });
    }
}
