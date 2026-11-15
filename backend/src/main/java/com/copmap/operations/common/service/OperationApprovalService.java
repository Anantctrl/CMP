package com.copmap.operations.common.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.operations.common.dto.OperationApprovalResponse;
import com.copmap.operations.common.entity.OperationApprovalRequest;
import com.copmap.operations.common.entity.OperationTeam;
import com.copmap.operations.common.repository.OperationApprovalRequestRepository;
import com.copmap.operations.common.repository.OperationTeamRepository;
import com.copmap.team.service.TeamAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationApprovalService {

    private final OperationApprovalRequestRepository approvalRequestRepository;
    private final OperationTeamRepository operationTeamRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TeamAuthorizationService authService;
    private final OperationAuditService auditService;

    public OperationApprovalService(
            OperationApprovalRequestRepository approvalRequestRepository,
            OperationTeamRepository operationTeamRepository,
            CurrentOfficerService currentOfficerService,
            TeamAuthorizationService authService,
            OperationAuditService auditService
    ) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.operationTeamRepository = operationTeamRepository;
        this.currentOfficerService = currentOfficerService;
        this.authService = authService;
        this.auditService = auditService;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<OperationApprovalResponse> getApprovalRequests(String status) {
        OfficerProfile currentOfficer = currentOfficerService.getCurrentOfficer();

        List<OperationApprovalRequest> requests;
        if (authService.isSupervisor()) {
            if (status != null && !status.isBlank()) {
                requests = approvalRequestRepository.findAllByStatus(status.trim().toUpperCase());
            } else {
                requests = approvalRequestRepository.findAll();
            }
        } else {
            requests = approvalRequestRepository.findAllByRequestedBy_Id(currentOfficer.getId());
        }

        return requests.stream().map(OperationApprovalResponse::from).toList();
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationApprovalResponse approveRequest(UUID approvalId, String remarks) {
        authService.requireSupervisor("Only supervisors can approve cross-team operation requests");

        OperationApprovalRequest request = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Only pending approval requests can be approved");
        }

        OfficerProfile supervisor = currentOfficerService.getCurrentOfficer();
        OffsetDateTime now = OffsetDateTime.now();

        request.setStatus("APPROVED");
        request.setReviewedBy(supervisor);
        request.setReviewedAt(now);
        request.setReviewRemarks(remarks != null ? remarks.trim() : "Approved by supervisor");

        OperationApprovalRequest saved = approvalRequestRepository.save(request);

        // Update OperationTeam status to CONFIRMED
        operationTeamRepository.findByOperation_IdAndTeam_Id(
                request.getOperation().getId(),
                request.getTargetTeam().getId()
        ).ifPresent(ot -> {
            ot.setStatus("CONFIRMED");
            operationTeamRepository.save(ot);
        });

        auditService.record(
                request.getOperation(),
                null,
                null,
                supervisor,
                "OPERATION_CROSS_TEAM_APPROVED",
                Map.of("targetTeamId", request.getTargetTeam().getId().toString(), "status", "PENDING"),
                Map.of("status", "APPROVED", "reviewedAt", now.toString()),
                remarks != null ? remarks.trim() : "Cross-team operation request approved"
        );

        return OperationApprovalResponse.from(saved);
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationApprovalResponse rejectRequest(UUID approvalId, String remarks) {
        authService.requireSupervisor("Only supervisors can reject cross-team operation requests");

        OperationApprovalRequest request = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Only pending approval requests can be rejected");
        }

        OfficerProfile supervisor = currentOfficerService.getCurrentOfficer();
        OffsetDateTime now = OffsetDateTime.now();

        request.setStatus("REJECTED");
        request.setReviewedBy(supervisor);
        request.setReviewedAt(now);
        request.setReviewRemarks(remarks != null ? remarks.trim() : "Rejected by supervisor");

        OperationApprovalRequest saved = approvalRequestRepository.save(request);

        // Update OperationTeam status to REJECTED
        operationTeamRepository.findByOperation_IdAndTeam_Id(
                request.getOperation().getId(),
                request.getTargetTeam().getId()
        ).ifPresent(ot -> {
            ot.setStatus("REJECTED");
            operationTeamRepository.save(ot);
        });

        auditService.record(
                request.getOperation(),
                null,
                null,
                supervisor,
                "OPERATION_CROSS_TEAM_REJECTED",
                Map.of("targetTeamId", request.getTargetTeam().getId().toString(), "status", "PENDING"),
                Map.of("status", "REJECTED", "reviewedAt", now.toString()),
                remarks != null ? remarks.trim() : "Cross-team operation request rejected"
        );

        return OperationApprovalResponse.from(saved);
    }
}
