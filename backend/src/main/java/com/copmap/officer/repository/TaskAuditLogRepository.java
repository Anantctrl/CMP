package com.copmap.officer.repository;

import com.copmap.officer.entity.TaskAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskAuditLogRepository
        extends JpaRepository<TaskAuditLog, UUID> {

    List<TaskAuditLog> findByAssignmentIdOrderByCreatedAtDesc(
            UUID assignmentId
    );
}
