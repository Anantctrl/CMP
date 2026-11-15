package com.copmap.operations.common.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationApprovalRequest;
import com.copmap.operations.common.entity.OperationTeam;
import com.copmap.operations.common.enums.OperationAssignmentStatus;
import com.copmap.operations.common.enums.OperationStatus;
import com.copmap.operations.common.enums.OperationType;
import com.copmap.operations.common.repository.OperationApprovalRequestRepository;
import com.copmap.operations.common.repository.OperationAssignmentRepository;
import com.copmap.operations.common.repository.OperationRepository;
import com.copmap.operations.common.repository.OperationTeamRepository;
import com.copmap.team.entity.Team;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamMembershipRepository;
import com.copmap.team.repository.TeamRepository;
import com.copmap.team.service.TeamAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OperationService {

    private final OperationRepository operationRepository;
    private final CurrentOfficerService currentOfficerService;
    private final OperationAuditService auditService;
    private final OperationAssignmentRepository assignmentRepository;
    private final OperationTeamRepository operationTeamRepository;
    private final OperationApprovalRequestRepository approvalRequestRepository;
    private final TeamRepository teamRepository;
    private final TeamAuthorizationService authService;
    private final TeamMembershipRepository membershipRepository;

    public OperationService(
            OperationRepository operationRepository,
            CurrentOfficerService currentOfficerService,
            OperationAuditService auditService,
            OperationAssignmentRepository assignmentRepository,
            OperationTeamRepository operationTeamRepository,
            OperationApprovalRequestRepository approvalRequestRepository,
            TeamRepository teamRepository,
            TeamAuthorizationService authService,
            TeamMembershipRepository membershipRepository
    ) {
        this.operationRepository = operationRepository;
        this.currentOfficerService = currentOfficerService;
        this.auditService = auditService;
        this.assignmentRepository = assignmentRepository;
        this.operationTeamRepository = operationTeamRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.teamRepository = teamRepository;
        this.authService = authService;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public Operation createOperation(
            OperationType type,
            String title,
            String description,
            Set<UUID> teamIds,
            String requestRemarks
    ) {
        OfficerProfile creator = currentOfficerService.getCurrentOfficer();

        boolean isSupervisor = authService.isSupervisor();
        boolean isPlanner = isSupervisor || membershipRepository.findAllByOfficer_Id(creator.getId()).stream()
                .anyMatch(tm -> tm.getTeamRole() == TeamRole.SENIOR_PLANNER);

        if (!isPlanner) {
            throw new AccessDeniedException("Only supervisors or senior planners can create operations");
        }

        if (type == null) {
            throw new IllegalArgumentException("Operation type is required");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Operation title is required");
        }

        if (title.trim().length() > 150) {
            throw new IllegalArgumentException("Operation title cannot exceed 150 characters");
        }

        Operation operation = new Operation();
        operation.setType(type);
        operation.setTitle(title.trim());
        operation.setDescription(description);
        operation.setCreatedBy(creator);
        operation.setStatus(OperationStatus.DRAFT);

        Operation savedOperation = operationRepository.save(operation);

        // Process participating teams
        if (teamIds != null && !teamIds.isEmpty()) {
            for (UUID teamId : teamIds) {
                Team team = teamRepository.findById(teamId)
                        .orElseThrow(() -> new EntityNotFoundException("Team not found: " + teamId));

                boolean hasPlannerInTeam = isSupervisor || authService.hasTeamRole(teamId, TeamRole.SENIOR_PLANNER);

                OperationTeam opTeam = new OperationTeam();
                opTeam.setOperation(savedOperation);
                opTeam.setTeam(team);

                if (hasPlannerInTeam) {
                    opTeam.setStatus("CONFIRMED");
                    operationTeamRepository.save(opTeam);
                } else {
                    opTeam.setStatus("PENDING_APPROVAL");
                    operationTeamRepository.save(opTeam);

                    // Create approval request for supervisor
                    OperationApprovalRequest approvalReq = new OperationApprovalRequest();
                    approvalReq.setOperation(savedOperation);
                    approvalReq.setRequestedBy(creator);
                    approvalReq.setTargetTeam(team);
                    approvalReq.setStatus("PENDING");
                    approvalReq.setRequestRemarks(requestRemarks != null ? requestRemarks.trim() : "Cross-team operation request");
                    approvalRequestRepository.save(approvalReq);
                }
            }
        }

        auditService.record(
                savedOperation,
                null,
                null,
                creator,
                "OPERATION_CREATED",
                null,
                Map.of(
                        "type", savedOperation.getType().name(),
                        "title", savedOperation.getTitle(),
                        "status", savedOperation.getStatus().name()
                ),
                "Operation created"
        );

        return savedOperation;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public Operation changeOperationStatus(
            UUID operationId,
            OperationStatus newStatus,
            String remarks
    ) {
        verifyOperationManagerAccess(operationId);

        if (newStatus == null) {
            throw new IllegalArgumentException("New operation status is required");
        }

        Operation operation = getOperation(operationId);
        OperationStatus oldStatus = operation.getStatus();

        if (!isValidTransition(oldStatus, newStatus)) {
            throw new IllegalStateException("Invalid operation status transition: " + oldStatus + " -> " + newStatus);
        }

        if (newStatus == OperationStatus.COMPLETED || newStatus == OperationStatus.CANCELLED) {
            boolean hasActiveAssignments = assignmentRepository
                    .findAllByLocation_Operation_Id(operationId)
                    .stream()
                    .anyMatch(assignment ->
                            assignment.getStatus() == OperationAssignmentStatus.ASSIGNED
                                    || assignment.getStatus() == OperationAssignmentStatus.ACKNOWLEDGED
                                    || assignment.getStatus() == OperationAssignmentStatus.IN_PROGRESS
                    );

            if (hasActiveAssignments) {
                throw new IllegalStateException(
                        "All officer assignments must be completed or cancelled before closing the operation"
                );
            }
        }

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        OffsetDateTime now = OffsetDateTime.now();

        operation.setStatus(newStatus);

        if (newStatus == OperationStatus.ACTIVE && operation.getActualStartAt() == null) {
            operation.setActualStartAt(now);
        }

        if (newStatus == OperationStatus.COMPLETED || newStatus == OperationStatus.CANCELLED) {
            operation.setActualEndAt(now);
        }

        Operation savedOperation = operationRepository.save(operation);

        auditService.record(
                savedOperation,
                null,
                null,
                changedBy,
                "OPERATION_STATUS_CHANGED",
                Map.of("status", oldStatus.name()),
                Map.of("status", newStatus.name(), "changedAt", now.toString()),
                remarks == null || remarks.isBlank() ? "Operation status changed" : remarks.trim()
        );

        return savedOperation;
    }

    private boolean isValidTransition(OperationStatus current, OperationStatus next) {
        return switch (current) {
            case DRAFT -> next == OperationStatus.PLANNED || next == OperationStatus.CANCELLED;
            case PLANNED -> next == OperationStatus.ACTIVE || next == OperationStatus.CANCELLED;
            case ACTIVE -> next == OperationStatus.PAUSED || next == OperationStatus.COMPLETED || next == OperationStatus.CANCELLED;
            case PAUSED -> next == OperationStatus.ACTIVE || next == OperationStatus.COMPLETED || next == OperationStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public Operation scheduleOperation(
            UUID operationId,
            OffsetDateTime plannedStartAt,
            OffsetDateTime plannedEndAt,
            String remarks
    ) {
        verifyOperationManagerAccess(operationId);

        if (plannedStartAt == null || plannedEndAt == null) {
            throw new IllegalArgumentException("Planned start and end times are required");
        }

        if (!plannedEndAt.isAfter(plannedStartAt)) {
            throw new IllegalArgumentException("Planned end time must be after planned start time");
        }

        // Verify no pending cross-team approvals exist
        if (approvalRequestRepository.existsByOperation_IdAndStatus(operationId, "PENDING")) {
            throw new IllegalStateException("Cannot schedule operation with pending cross-team approvals");
        }

        Operation operation = getOperation(operationId);
        OperationStatus oldStatus = operation.getStatus();

        if (oldStatus != OperationStatus.DRAFT && oldStatus != OperationStatus.PLANNED) {
            throw new IllegalStateException("Only DRAFT or PLANNED operations can be scheduled");
        }

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        OffsetDateTime oldStart = operation.getPlannedStartAt();
        OffsetDateTime oldEnd = operation.getPlannedEndAt();

        operation.setPlannedStartAt(plannedStartAt);
        operation.setPlannedEndAt(plannedEndAt);

        if (oldStatus == OperationStatus.DRAFT) {
            operation.setStatus(OperationStatus.PLANNED);
        }

        Operation savedOperation = operationRepository.save(operation);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("plannedStartAt", oldStart == null ? null : oldStart.toString());
        oldValues.put("plannedEndAt", oldEnd == null ? null : oldEnd.toString());
        oldValues.put("status", oldStatus.name());

        Map<String, Object> newValues = Map.of(
                "plannedStartAt", plannedStartAt.toString(),
                "plannedEndAt", plannedEndAt.toString(),
                "status", savedOperation.getStatus().name()
        );

        auditService.record(
                savedOperation,
                null,
                null,
                changedBy,
                "OPERATION_SCHEDULED",
                oldValues,
                newValues,
                remarks == null || remarks.isBlank() ? "Operation scheduled" : remarks.trim()
        );

        return savedOperation;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public Operation getOperation(UUID operationId) {
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Operation not found: " + operationId));

        verifyOperationReadAccess(operationId);
        return operation;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Operation> getAllOperations() {
        if (authService.isSupervisor()) {
            return operationRepository.findAll();
        }

        OfficerProfile officer = currentOfficerService.getCurrentOfficer();
        List<UUID> myTeamIds = membershipRepository.findTeamIdsByOfficerId(officer.getId());

        Set<UUID> operationIds = new HashSet<>();

        // Operations involving officer's teams
        for (UUID teamId : myTeamIds) {
            operationTeamRepository.findAllByTeam_Id(teamId)
                    .forEach(ot -> operationIds.add(ot.getOperation().getId()));
        }

        // Operations where officer has direct assignment
        assignmentRepository.findDistinctOperationsByOfficerId(officer.getId())
                .forEach(op -> operationIds.add(op.getId()));

        if (operationIds.isEmpty()) {
            return List.of();
        }

        return operationRepository.findAllById(operationIds);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Operation> getOperationsByType(OperationType type) {
        if (type == null) {
            throw new IllegalArgumentException("Operation type is required");
        }
        return getAllOperations().stream()
                .filter(op -> op.getType() == type)
                .toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Operation> getOperationsByStatus(OperationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Operation status is required");
        }
        return getAllOperations().stream()
                .filter(op -> op.getStatus() == status)
                .toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Operation> getOperationsByTypeAndStatus(OperationType type, OperationStatus status) {
        if (type == null || status == null) {
            throw new IllegalArgumentException("Operation type and status are required");
        }
        return getAllOperations().stream()
                .filter(op -> op.getType() == type && op.getStatus() == status)
                .toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<UUID> getParticipatingTeamIds(UUID operationId) {
        return operationTeamRepository.findAllByOperation_Id(operationId).stream()
                .map(ot -> ot.getTeam().getId())
                .toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public boolean hasPendingApproval(UUID operationId) {
        return approvalRequestRepository.existsByOperation_IdAndStatus(operationId, "PENDING");
    }

    private void verifyOperationReadAccess(UUID operationId) {
        if (authService.isSupervisor()) {
            return;
        }

        OfficerProfile officer = currentOfficerService.getCurrentOfficer();
        List<UUID> myTeamIds = membershipRepository.findTeamIdsByOfficerId(officer.getId());

        boolean isTeamInvolved = operationTeamRepository.findAllByOperation_Id(operationId).stream()
                .anyMatch(ot -> myTeamIds.contains(ot.getTeam().getId()));

        boolean isAssigned = assignmentRepository.existsByLocation_Operation_IdAndOfficer_Id(
                operationId,
                officer.getId()
        );

        if (!isTeamInvolved && !isAssigned) {
            throw new AccessDeniedException("You are not authorized to view this operation");
        }
    }

    private void verifyOperationManagerAccess(UUID operationId) {
        if (authService.isSupervisor()) {
            return;
        }

        OfficerProfile officer = currentOfficerService.getCurrentOfficer();
        List<UUID> myTeamIds = membershipRepository.findTeamIdsByOfficerId(officer.getId());

        List<OperationTeam> opTeams = operationTeamRepository.findAllByOperation_Id(operationId);

        boolean hasManagerRole = opTeams.stream().anyMatch(ot -> {
            if (myTeamIds.contains(ot.getTeam().getId())) {
                return authService.hasTeamRole(ot.getTeam().getId(), TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD);
            }
            return false;
        });

        if (!hasManagerRole) {
            throw new AccessDeniedException("Only supervisors, senior planners, or team leads of participating teams can manage this operation");
        }
    }
}
