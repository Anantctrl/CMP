package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.OperationAuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationAuditLogRepository
        extends JpaRepository<OperationAuditLog, UUID> {

    boolean existsByLocation_Id(UUID locationId);

    List<OperationAuditLog> findAllByOperation_IdOrderByCreatedAtDesc(
            UUID operationId
    );
}
