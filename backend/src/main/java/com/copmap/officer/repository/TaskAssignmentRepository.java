package com.copmap.officer.repository;

import com.copmap.officer.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskAssignmentRepository
        extends JpaRepository<TaskAssignment, UUID> {

    @Query("SELECT ta FROM TaskAssignment ta LEFT JOIN FETCH ta.officer LEFT JOIN FETCH ta.task WHERE ta.task.id = :taskId")
    List<TaskAssignment> findByTaskIdWithOfficer(@Param("taskId") UUID taskId);

    List<TaskAssignment> findByTaskId(UUID taskId);

    @Query("SELECT ta FROM TaskAssignment ta LEFT JOIN FETCH ta.officer LEFT JOIN FETCH ta.task WHERE ta.officer.id = :officerId")
    List<TaskAssignment> findByOfficerIdWithDetails(@Param("officerId") UUID officerId);

    List<TaskAssignment> findByOfficerId(UUID officerId);

    List<TaskAssignment> findByOfficerIdAndStatus(
            UUID officerId,
            String status
    );

    boolean existsByTaskIdAndOfficerId(UUID taskId, UUID officerId);
}
