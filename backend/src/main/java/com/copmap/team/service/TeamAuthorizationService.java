package com.copmap.team.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.team.entity.Team;
import com.copmap.team.entity.TeamMembership;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamMembershipRepository;
import com.copmap.team.repository.TeamRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeamAuthorizationService {

    private final CurrentOfficerService currentOfficerService;
    private final TeamMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;

    public TeamAuthorizationService(
            CurrentOfficerService currentOfficerService,
            TeamMembershipRepository membershipRepository,
            TeamRepository teamRepository
    ) {
        this.currentOfficerService = currentOfficerService;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
    }

    public boolean isSupervisor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "Supervisor".equalsIgnoreCase(authority.getAuthority())
                                || "Administrator".equalsIgnoreCase(authority.getAuthority())
                );
    }

    public void requireSupervisor(String message) {
        if (!isSupervisor()) {
            throw new AccessDeniedException(
                    message != null ? message : "Only supervisors can perform this action"
            );
        }
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public boolean hasTeamRole(UUID teamId, TeamRole... allowedRoles) {
        if (isSupervisor()) {
            return true;
        }

        OfficerProfile officer = currentOfficerService.getCurrentOfficer();
        Optional<TeamMembership> membership =
                membershipRepository.findByTeam_IdAndOfficer_Id(teamId, officer.getId());

        if (membership.isEmpty()) {
            return false;
        }

        TeamRole memberRole = membership.get().getTeamRole();
        return Arrays.asList(allowedRoles).contains(memberRole);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public void requireTeamRole(UUID teamId, String message, TeamRole... allowedRoles) {
        if (!hasTeamRole(teamId, allowedRoles)) {
            throw new AccessDeniedException(
                    message != null ? message : "You are not authorized for this team operation"
            );
        }
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<UUID> getAuthorizedTeamIds() {
        if (isSupervisor()) {
            return teamRepository.findAll().stream().map(Team::getId).toList();
        }

        OfficerProfile officer = currentOfficerService.getCurrentOfficer();
        return membershipRepository.findTeamIdsByOfficerId(officer.getId());
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public Optional<TeamRole> getOfficerTeamRole(UUID teamId, UUID officerId) {
        return membershipRepository.findByTeam_IdAndOfficer_Id(teamId, officerId)
                .map(TeamMembership::getTeamRole);
    }
}
