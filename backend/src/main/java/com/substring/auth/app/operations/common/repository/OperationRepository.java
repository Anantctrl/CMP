package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.Operation;
import com.substring.auth.app.operations.common.enums.OperationStatus;
import com.substring.auth.app.operations.common.enums.OperationType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationRepository
        extends JpaRepository<Operation, UUID> {

    List<Operation> findAllByType(OperationType type);

    List<Operation> findAllByStatus(OperationStatus status);

    List<Operation> findAllByTypeAndStatus(
            OperationType type,
            OperationStatus status
    );
}
