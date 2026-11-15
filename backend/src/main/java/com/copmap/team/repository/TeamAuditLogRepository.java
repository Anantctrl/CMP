package com.copmap.team.repository;

import com.copmap.team.entity.TeamAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamAuditLogRepository extends JpaRepository<TeamAuditLog, UUID> {
    List<TeamAuditLog> findAllByTeam_IdOrderByCreatedAtDesc(UUID teamId);
}
