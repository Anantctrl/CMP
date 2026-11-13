package com.substring.auth.app.operations.common.service;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.service.CurrentOfficerService;
import com.substring.auth.app.operations.common.entity.OperationAssignment;
import com.substring.auth.app.operations.common.entity.OperationLocation;
import com.substring.auth.app.operations.common.enums.OperationAssignmentStatus;
import com.substring.auth.app.operations.common.enums.OperationStatus;
import com.substring.auth.app.operations.common.repository.OperationAssignmentRepository;
import com.substring.auth.app.operations.common.repository.OperationLocationRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationAssignmentService {

    private final OperationAssignmentRepository assignmentRepository;
    private final OperationLocationRepository locationRepository;
    private final OfficerProfileRepository officerRepository;
    private final OperationAuditService auditService;
    private final CurrentOfficerService currentOfficerService;

    private static final EnumSet<OperationAssignmentStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    OperationAssignmentStatus.ASSIGNED,
                    OperationAssignmentStatus.ACKNOWLEDGED,
                    OperationAssignmentStatus.IN_PROGRESS
            );

    public OperationAssignmentService(
            OperationAssignmentRepository assignmentRepository,
            OperationLocationRepository locationRepository,
            OfficerProfileRepository officerRepository,
            OperationAuditService auditService,
            CurrentOfficerService currentOfficerService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.locationRepository = locationRepository;
        this.officerRepository = officerRepository;
        this.auditService = auditService;
        this.currentOfficerService = currentOfficerService;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationAssignment assignOfficer(
            UUID locationId,
            UUID officerId,
            String responsibility,
            String instructions
    ) {

        verifySupervisorOrAdmin();

        if (responsibility == null || responsibility.isBlank()) {
            throw new IllegalArgumentException(
                    "Responsibility is required"
            );
        }

        if (responsibility.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Responsibility cannot exceed 150 characters"
            );
        }

        OperationLocation location = locationRepository.findById(locationId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Operation location not found")
                );

        OperationStatus operationStatus =
                location.getOperation().getStatus();

        if (operationStatus != OperationStatus.PLANNED
                && operationStatus != OperationStatus.ACTIVE) {

            throw new IllegalStateException(
                    "Officers can only be assigned to planned or active operations"
            );
        }

        OfficerProfile officer = officerRepository.findById(officerId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Officer not found")
                );

        OfficerProfile assignedBy =
                currentOfficerService.getCurrentOfficer();

        boolean alreadyAssigned =
                assignmentRepository
                        .existsByLocation_IdAndOfficer_IdAndStatusIn(
                                locationId,
                                officerId,
                                ACTIVE_STATUSES
                        );

        if (alreadyAssigned) {
            throw new IllegalArgumentException(
                    "This officer already has an active assignment at this location"
            );
        }

        OperationAssignment assignment = new OperationAssignment();

        assignment.setLocation(location);
        assignment.setOfficer(officer);
        assignment.setAssignedBy(assignedBy);
        assignment.setResponsibility(responsibility.trim());
        assignment.setInstructions(instructions);
        assignment.setStatus(OperationAssignmentStatus.ASSIGNED);

        OperationAssignment saved =
                assignmentRepository.save(assignment);

        auditService.record(
                location.getOperation(),
                location,
                saved,
                assignedBy,
                "OPERATION_OFFICER_ASSIGNED",
                null,
                Map.of(
                        "officerId", officer.getId().toString(),
                        "responsibility", responsibility.trim(),
                        "status", OperationAssignmentStatus.ASSIGNED.name()
                ),
                "Officer assigned to operation location"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationAssignment acknowledgeAssignment(UUID assignmentId) {

        OperationAssignment assignment = getAssignment(assignmentId);

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        verifyAssignmentOwner(assignment, changedBy);

        if (assignment.getStatus() != OperationAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Only assigned operations can be acknowledged"
            );
        }

        OperationAssignmentStatus oldStatus = assignment.getStatus();

        assignment.setStatus(OperationAssignmentStatus.ACKNOWLEDGED);
        assignment.setAcknowledgedAt(OffsetDateTime.now());

        OperationAssignment saved =
                assignmentRepository.save(assignment);

        auditService.record(
                assignment.getLocation().getOperation(),
                assignment.getLocation(),
                saved,
                changedBy,
                "OPERATION_ASSIGNMENT_ACKNOWLEDGED",
                Map.of("status", oldStatus.name()),
                Map.of(
                        "status", saved.getStatus().name(),
                        "acknowledgedAt",
                        saved.getAcknowledgedAt().toString()
                ),
                "Officer acknowledged operation assignment"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationAssignment startAssignment(UUID assignmentId) {

        OperationAssignment assignment = getAssignment(assignmentId);

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        verifyAssignmentOwner(assignment, changedBy);

        if (assignment.getLocation().getOperation().getStatus()
                != OperationStatus.ACTIVE) {

            throw new IllegalStateException(
                    "An assignment can only be started while its operation is active"
            );
        }

        if (assignment.getStatus() != OperationAssignmentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    "Only acknowledged assignments can be started"
            );
        }

        OperationAssignmentStatus oldStatus = assignment.getStatus();

        assignment.setStatus(OperationAssignmentStatus.IN_PROGRESS);
        assignment.setStartedAt(OffsetDateTime.now());

        OperationAssignment saved =
                assignmentRepository.save(assignment);

        auditService.record(
                assignment.getLocation().getOperation(),
                assignment.getLocation(),
                saved,
                changedBy,
                "OPERATION_ASSIGNMENT_STARTED",
                Map.of("status", oldStatus.name()),
                Map.of(
                        "status", saved.getStatus().name(),
                        "startedAt", saved.getStartedAt().toString()
                ),
                "Officer started operation assignment"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationAssignment submitCompletion(
            UUID assignmentId,
            String completionReport
    ) {

        OperationAssignment assignment = getAssignment(assignmentId);

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        verifyAssignmentOwner(assignment, changedBy);

        if (assignment.getStatus() != OperationAssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Only in-progress assignments can be completed"
            );
        }

        if (completionReport == null || completionReport.isBlank()) {
            throw new IllegalArgumentException(
                    "Completion report is required"
            );
        }

        OperationAssignmentStatus oldStatus = assignment.getStatus();

        assignment.setCompletionReport(completionReport.trim());
        assignment.setStatus(OperationAssignmentStatus.COMPLETED);
        assignment.setCompletedAt(OffsetDateTime.now());

        OperationAssignment saved =
                assignmentRepository.save(assignment);

        auditService.record(
                assignment.getLocation().getOperation(),
                assignment.getLocation(),
                saved,
                changedBy,
                "OPERATION_ASSIGNMENT_COMPLETED",
                Map.of("status", oldStatus.name()),
                Map.of(
                        "status", saved.getStatus().name(),
                        "completionReport", saved.getCompletionReport(),
                        "completedAt", saved.getCompletedAt().toString()
                ),
                "Officer submitted operation completion report"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationAssignment cancelAssignment(
            UUID assignmentId,
            String reason
    ) {

        verifySupervisorOrAdmin();

        OperationAssignment assignment = getAssignment(assignmentId);

        if (assignment.getStatus() == OperationAssignmentStatus.COMPLETED
                || assignment.getStatus() == OperationAssignmentStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Completed or cancelled assignments cannot be cancelled again"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Cancellation reason is required"
            );
        }

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        OperationAssignmentStatus oldStatus = assignment.getStatus();

        assignment.setStatus(OperationAssignmentStatus.CANCELLED);

        OperationAssignment saved =
                assignmentRepository.save(assignment);

        auditService.record(
                assignment.getLocation().getOperation(),
                assignment.getLocation(),
                saved,
                changedBy,
                "OPERATION_ASSIGNMENT_CANCELLED",
                Map.of("status", oldStatus.name()),
                Map.of(
                        "status", OperationAssignmentStatus.CANCELLED.name()
                ),
                reason.trim()
        );

        return saved;
    }

    // Get all assignments belonging to the logged-in officer.
    // Includes completed and cancelled assignments for history.
    @Transactional(
            transactionManager = "postgresTransactionManager",
            readOnly = true
    )
    public List<OperationAssignment> getMyAssignments() {

        OfficerProfile currentOfficer =
                currentOfficerService.getCurrentOfficer();

        return assignmentRepository.findAllByOfficer_Id(
                currentOfficer.getId()
        );
    }

    // Get one assignment.
    // Officers can access only their own assignments.
    // Supervisors and administrators can access any assignment.
    @Transactional(
            transactionManager = "postgresTransactionManager",
            readOnly = true
    )
    public OperationAssignment getAssignmentById(UUID assignmentId) {

        OperationAssignment assignment = getAssignment(assignmentId);

        if (!isSupervisorOrAdmin()) {

            OfficerProfile currentOfficer =
                    currentOfficerService.getCurrentOfficer();

            verifyAssignmentOwner(assignment, currentOfficer);
        }

        return assignment;
    }

    // Get assignments for an operation.
    // Supervisors and administrators see all officers.
    // Regular officers see only their own assignments.
    @Transactional(
            transactionManager = "postgresTransactionManager",
            readOnly = true
    )
    public List<OperationAssignment> getAssignmentsByOperation(
            UUID operationId
    ) {

        List<OperationAssignment> assignments =
                assignmentRepository.findAllByLocation_Operation_Id(
                        operationId
                );

        if (isSupervisorOrAdmin()) {
            return assignments;
        }

        OfficerProfile currentOfficer =
                currentOfficerService.getCurrentOfficer();

        List<OperationAssignment> myAssignments =
                assignments.stream()
                        .filter(assignment ->
                                assignment.getOfficer().getId()
                                        .equals(currentOfficer.getId())
                        )
                        .toList();

        if (myAssignments.isEmpty()) {
            throw new AccessDeniedException(
                    "You are not assigned to this operation"
            );
        }

        return myAssignments;
    }

    private boolean isSupervisorOrAdmin() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("Supervisor")
                                || authority.getAuthority().equals("Administrator")
                );
    }

    private OperationAssignment getAssignment(UUID assignmentId) {

        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Operation assignment not found"
                        )
                );
    }

    private void verifyAssignmentOwner(
            OperationAssignment assignment,
            OfficerProfile currentOfficer
    ) {

        if (!assignment.getOfficer().getId()
                .equals(currentOfficer.getId())) {

            throw new AccessDeniedException(
                    "You are not authorized to modify this assignment"
            );
        }
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
                    "Only supervisors or administrators can perform this action"
            );
        }
    }
}
