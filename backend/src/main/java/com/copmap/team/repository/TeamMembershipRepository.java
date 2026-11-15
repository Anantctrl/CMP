package com.copmap.team.repository;

import com.copmap.team.entity.TeamMembership;
import com.copmap.team.enums.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {

    List<TeamMembership> findAllByTeam_Id(UUID teamId);

    List<TeamMembership> findAllByOfficer_Id(UUID officerId);

    Optional<TeamMembership> findByTeam_IdAndOfficer_Id(UUID teamId, UUID officerId);

    boolean existsByTeam_IdAndOfficer_Id(UUID teamId, UUID officerId);

    boolean existsByTeam_IdAndOfficer_IdAndTeamRole(UUID teamId, UUID officerId, TeamRole teamRole);

    long countByTeam_Id(UUID teamId);

    void deleteByTeam_IdAndOfficer_Id(UUID teamId, UUID officerId);

    @Query("SELECT tm.team.id FROM TeamMembership tm WHERE tm.officer.id = :officerId")
    List<UUID> findTeamIdsByOfficerId(@Param("officerId") UUID officerId);
}
