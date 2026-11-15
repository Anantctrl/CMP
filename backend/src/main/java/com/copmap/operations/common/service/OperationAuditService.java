package com.copmap.operations.common.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationAssignment;
import com.copmap.operations.common.entity.OperationAuditLog;
import com.copmap.operations.common.entity.OperationLocation;
import com.copmap.operations.common.repository.OperationAuditLogRepository;
import com.copmap.operations.common.repository.OperationRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationAuditService {

    private final OperationAuditLogRepository auditRepository;
    private final OperationRepository operationRepository;

    public OperationAuditService(
            OperationAuditLogRepository auditRepository,
            OperationRepository operationRepository
    ) {
        this.auditRepository = auditRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public void record(
            Operation operation,
            OperationLocation location,
            OperationAssignment assignment,
            OfficerProfile changedBy,
            String action,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            String remarks
    ) {
        OperationAuditLog log = new OperationAuditLog();

        log.setOperation(operation);
        log.setLocation(location);
        log.setAssignment(assignment);
        log.setChangedBy(changedBy);
        log.setAction(action);
        log.setOldValues(oldValues);
        log.setNewValues(newValues);
        log.setRemarks(remarks);

        auditRepository.save(log);
    }

    @Transactional(
            transactionManager = "postgresTransactionManager",
            readOnly = true
    )
    public List<OperationAuditLog> getOperationAuditHistory(
            UUID operationId
    ) {

        verifySupervisorOrAdmin();

        if (!operationRepository.existsById(operationId)) {
            throw new EntityNotFoundException("Operation not found");
        }

        return auditRepository
                .findAllByOperation_IdOrderByCreatedAtDesc(operationId);
    }

    private void verifySupervisorOrAdmin() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        boolean authorized = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("Supervisor")
                                || authority.getAuthority().equals("Administrator")
                );

        if (!authorized) {
            throw new AccessDeniedException(
                    "Only supervisors or administrators can view operation audit history"
            );
        }
    }
}
