package com.substring.auth.app.officer.repository;

import com.substring.auth.app.officer.entity.TaskAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskAuditLogRepository
        extends JpaRepository<TaskAuditLog, UUID> {

    List<TaskAuditLog> findByAssignmentIdOrderByCreatedAtDesc(
            UUID assignmentId
    );
}
