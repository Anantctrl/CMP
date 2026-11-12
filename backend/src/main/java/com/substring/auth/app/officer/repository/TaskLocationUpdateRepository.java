package com.substring.auth.app.officer.repository;

import com.substring.auth.app.officer.entity.TaskLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskLocationUpdateRepository
        extends JpaRepository<TaskLocationUpdate, UUID> {

    List<TaskLocationUpdate> findByAssignmentIdOrderByRecordedAtDesc(
            UUID assignmentId
    );

    Optional<TaskLocationUpdate> findFirstByAssignmentIdOrderByRecordedAtDesc(
            UUID assignmentId
    );
}
