package com.substring.auth.app.operations.common.repository;

import com.substring.auth.app.operations.common.entity.OperationApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperationApprovalRequestRepository
        extends JpaRepository<OperationApprovalRequest, UUID> {

    List<OperationApprovalRequest> findAllByStatus(String status);

    List<OperationApprovalRequest> findAllByOperation_Id(UUID operationId);

    List<OperationApprovalRequest> findAllByRequestedBy_Id(UUID requestedById);

    boolean existsByOperation_IdAndStatus(UUID operationId, String status);
}
