package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.Operation;
import com.substring.auth.app.operations.common.entity.OperationAssignment;
import com.substring.auth.app.operations.common.enums.OperationAssignmentStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OperationAssignmentRepository
        extends JpaRepository<OperationAssignment, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "location",
            "location.operation",
            "officer",
            "assignedBy"
    })
    java.util.Optional<OperationAssignment> findById(UUID id);

    boolean existsByLocation_Id(UUID locationId);

    boolean existsByLocation_IdAndOfficer_IdAndStatusIn(
            UUID locationId,
            UUID officerId,
            Collection<OperationAssignmentStatus> statuses
    );

    boolean existsByLocation_Operation_IdAndOfficer_Id(
            UUID operationId,
            UUID officerId
    );

    @Query("""
            SELECT DISTINCT a.location.operation
            FROM OperationAssignment a
            WHERE a.officer.id = :officerId
            """)
    List<Operation> findDistinctOperationsByOfficerId(
            @Param("officerId") UUID officerId
    );

    @EntityGraph(attributePaths = {
            "location",
            "location.operation",
            "officer",
            "assignedBy"
    })
    List<OperationAssignment> findAllByLocation_Operation_Id(
            UUID operationId
    );

    @EntityGraph(attributePaths = {
            "location",
            "location.operation",
            "officer",
            "assignedBy"
    })
    List<OperationAssignment> findAllByOfficer_Id(
            UUID officerId
    );
}
