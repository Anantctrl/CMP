package com.substring.auth.app.team.service;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.service.CurrentOfficerService;
import com.substring.auth.app.operations.common.repository.OperationTeamRepository;
import com.substring.auth.app.team.dto.*;
import com.substring.auth.app.team.entity.Team;
import com.substring.auth.app.team.entity.TeamAuditLog;
import com.substring.auth.app.team.entity.TeamMembership;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.repository.TeamAuditLogRepository;
import com.substring.auth.app.team.repository.TeamMembershipRepository;
import com.substring.auth.app.team.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final TeamAuditLogRepository auditLogRepository;
    private final OfficerProfileRepository officerRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TeamAuthorizationService authService;
    private final OperationTeamRepository operationTeamRepository;

    public TeamService(
            TeamRepository teamRepository,
            TeamMembershipRepository membershipRepository,
            TeamAuditLogRepository auditLogRepository,
            OfficerProfileRepository officerRepository,
            CurrentOfficerService currentOfficerService,
            TeamAuthorizationService authService,
            OperationTeamRepository operationTeamRepository
    ) {
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.auditLogRepository = auditLogRepository;
        this.officerRepository = officerRepository;
        this.currentOfficerService = currentOfficerService;
        this.authService = authService;
        this.operationTeamRepository = operationTeamRepository;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TeamResponse createTeam(CreateTeamRequest request) {
        authService.requireSupervisor("Only supervisors can create teams");

        if (teamRepository.existsByName(request.name().trim())) {
            throw new IllegalArgumentException("A team with this name already exists");
        }

        OfficerProfile creator = currentOfficerService.getCurrentOfficer();

        Team team = new Team();
        team.setName(request.name().trim());
        team.setDescription(request.description());

        Team savedTeam = teamRepository.save(team);

        TeamAuditLog auditLog = new TeamAuditLog();
        auditLog.setTeam(savedTeam);
        auditLog.setChangedBy(creator);
        auditLog.setAction("TEAM_CREATED");
        auditLog.setNewValues(Map.of("name", savedTeam.getName(), "id", savedTeam.getId().toString()));
        auditLog.setRemarks("New team created");
        auditLogRepository.save(auditLog);

        return TeamResponse.from(savedTeam, 0, "SUPERVISOR", 0);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<TeamResponse> getAuthorizedTeams() {
        OfficerProfile currentOfficer = currentOfficerService.getCurrentOfficer();

        if (authService.isSupervisor()) {
            return teamRepository.findAll().stream()
                    .map(team -> {
                        long members = membershipRepository.countByTeam_Id(team.getId());
                        long ops = operationTeamRepository.findAllByTeam_Id(team.getId()).size();
                        return TeamResponse.from(team, members, "SUPERVISOR", ops);
                    })
                    .toList();
        }

        List<TeamMembership> myMemberships = membershipRepository.findAllByOfficer_Id(currentOfficer.getId());

        return myMemberships.stream()
                .map(membership -> {
                    Team team = membership.getTeam();
                    long members = membershipRepository.countByTeam_Id(team.getId());
                    long ops = operationTeamRepository.findAllByTeam_Id(team.getId()).size();
                    return TeamResponse.from(team, members, membership.getTeamRole().name(), ops);
                })
                .toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public TeamResponse getTeamDetail(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found"));

        OfficerProfile currentOfficer = currentOfficerService.getCurrentOfficer();
        String roleStr;

        if (authService.isSupervisor()) {
            roleStr = "SUPERVISOR";
        } else {
            TeamMembership tm = membershipRepository.findByTeam_IdAndOfficer_Id(teamId, currentOfficer.getId())
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "You are not a member of this team"
                    ));
            roleStr = tm.getTeamRole().name();
        }

        long members = membershipRepository.countByTeam_Id(team.getId());
        long ops = operationTeamRepository.findAllByTeam_Id(team.getId()).size();

        return TeamResponse.from(team, members, roleStr, ops);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<TeamMemberResponse> getTeamMembers(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new EntityNotFoundException("Team not found");
        }

        authService.requireTeamRole(
                teamId,
                "You are not authorized to view members of this team",
                TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.CONTROL_OPERATOR, TeamRole.ADMINISTRATOR, TeamRole.FIELD_OPERATOR
        );

        return membershipRepository.findAllByTeam_Id(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found"));

        authService.requireTeamRole(
                teamId,
                "Only Supervisors or Senior Planners in this team can add members",
                TeamRole.SENIOR_PLANNER
        );

        OfficerProfile targetOfficer = officerRepository.findById(request.officerId())
                .orElseThrow(() -> new EntityNotFoundException("Officer not found"));

        if (membershipRepository.existsByTeam_IdAndOfficer_Id(teamId, targetOfficer.getId())) {
            throw new IllegalArgumentException("Officer is already a member of this team");
        }

        OfficerProfile actor = currentOfficerService.getCurrentOfficer();

        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setOfficer(targetOfficer);
        membership.setTeamRole(request.teamRole());

        TeamMembership saved = membershipRepository.save(membership);

        TeamAuditLog auditLog = new TeamAuditLog();
        auditLog.setTeam(team);
        auditLog.setOfficer(targetOfficer);
        auditLog.setChangedBy(actor);
        auditLog.setAction("MEMBER_ADDED");
        auditLog.setNewValues(Map.of(
                "officerId", targetOfficer.getId().toString(),
                "teamRole", request.teamRole().name()
        ));
        auditLog.setRemarks("Officer added to team");
        auditLogRepository.save(auditLog);

        return TeamMemberResponse.from(saved);
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public void removeMember(UUID teamId, UUID officerId, String remarks) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found"));

        authService.requireTeamRole(
                teamId,
                "Only Supervisors or Senior Planners in this team can remove members",
                TeamRole.SENIOR_PLANNER
        );

        TeamMembership membership = membershipRepository.findByTeam_IdAndOfficer_Id(teamId, officerId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found in team"));

        OfficerProfile actor = currentOfficerService.getCurrentOfficer();

        // 1. Audit full member data before deletion as requested
        TeamAuditLog auditLog = new TeamAuditLog();
        auditLog.setTeam(team);
        auditLog.setOfficer(membership.getOfficer());
        auditLog.setChangedBy(actor);
        auditLog.setAction("MEMBER_REMOVED");
        auditLog.setOldValues(Map.of(
                "officerId", membership.getOfficer().getId().toString(),
                "teamRole", membership.getTeamRole().name(),
                "joinedAt", membership.getJoinedAt().toString()
        ));
        auditLog.setRemarks(remarks != null && !remarks.isBlank() ? remarks.trim() : "Member removed from team");
        auditLogRepository.save(auditLog);

        // 2. Hard delete the membership
        membershipRepository.delete(membership);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<TeamAuditResponse> getTeamAuditHistory(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new EntityNotFoundException("Team not found");
        }

        authService.requireTeamRole(
                teamId,
                "You are not authorized to view audit history for this team",
                TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.ADMINISTRATOR
        );

        return auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(teamId).stream()
                .map(TeamAuditResponse::from)
                .toList();
    }
}
