package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.OperationTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationTeamRepository extends JpaRepository<OperationTeam, UUID> {

    List<OperationTeam> findAllByOperation_Id(UUID operationId);

    List<OperationTeam> findAllByTeam_Id(UUID teamId);

    Optional<OperationTeam> findByOperation_IdAndTeam_Id(UUID operationId, UUID teamId);

    boolean existsByOperation_IdAndTeam_Id(UUID operationId, UUID teamId);

    void deleteAllByOperation_Id(UUID operationId);
}
