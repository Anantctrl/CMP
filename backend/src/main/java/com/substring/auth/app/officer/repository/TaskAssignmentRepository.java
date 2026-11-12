package com.substring.auth.app.officer.repository;

import com.substring.auth.app.officer.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskAssignmentRepository
        extends JpaRepository<TaskAssignment, UUID> {

    List<TaskAssignment> findByTaskId(UUID taskId);

    List<TaskAssignment> findByOfficerId(UUID officerId);

    List<TaskAssignment> findByOfficerIdAndStatus(
            UUID officerId,
            String status
    );

    boolean existsByTaskIdAndOfficerId(UUID taskId, UUID officerId);
}
