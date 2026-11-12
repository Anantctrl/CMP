package com.substring.auth.app.officer.service;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.entity.TaskAssignment;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.repository.TaskAssignmentRepository;
import com.substring.auth.app.officer.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskAssignmentService {

    private final TaskAssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;
    private final OfficerProfileRepository officerRepository;
    private final TaskAuditService auditService;
    private final CurrentOfficerService currentOfficerService;

    public TaskAssignmentService(
            TaskAssignmentRepository assignmentRepository,
            TaskRepository taskRepository,
            OfficerProfileRepository officerRepository,
            TaskAuditService auditService,
            CurrentOfficerService currentOfficerService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.officerRepository = officerRepository;
        this.auditService = auditService;
        this.currentOfficerService = currentOfficerService;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment assignOfficer(
            UUID taskId,
            UUID officerId,
            String responsibility,
            String instructions
    ) {

        if (responsibility == null || responsibility.isBlank()) {
            throw new IllegalArgumentException(
                    "Responsibility is required"
            );
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Task not found")
                );

        OfficerProfile officer = officerRepository.findById(officerId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Officer not found")
                );

        OfficerProfile assignedBy = currentOfficerService.getCurrentOfficer();

        if (assignmentRepository.existsByTaskIdAndOfficerId(
                taskId, officerId)) {
            throw new IllegalArgumentException(
                    "This officer is already assigned to this task"
            );
        }

        TaskAssignment assignment = new TaskAssignment();

        assignment.setTask(task);
        assignment.setOfficer(officer);
        assignment.setAssignedBy(assignedBy);
        assignment.setResponsibility(responsibility.trim());
        assignment.setInstructions(instructions);
        assignment.setStatus("ASSIGNED");

        TaskAssignment savedAssignment =
                assignmentRepository.save(assignment);

        auditService.record(
                task,
                savedAssignment,
                assignedBy,
                "OFFICER_ASSIGNED",
                null,
                Map.of(
                        "officerId", officer.getId().toString(),
                        "responsibility", responsibility.trim(),
                        "status", "ASSIGNED"
                ),
                "Officer assigned to task"
        );

        return savedAssignment;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment startAssignment(UUID assignmentId) {

        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found")
                );

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        verifyAssignmentOwner(assignment, changedBy);

        if (!"ASSIGNED".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Only assigned tasks can be started"
            );
        }

        Map<String, Object> oldValues = Map.of(
                "status", assignment.getStatus(),
                "startedAt", "null"
        );

        assignment.setStatus("IN_PROGRESS");
        assignment.setStartedAt(OffsetDateTime.now());

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "TASK_STARTED",
                oldValues,
                Map.of(
                        "status", saved.getStatus(),
                        "startedAt", saved.getStartedAt().toString()
                ),
                "Task assignment started"
        );

        return saved;
    }

    private void verifyAssignmentOwner(
            TaskAssignment assignment,
            OfficerProfile currentOfficer
    ) {
        if (!assignment.getOfficer().getId()
                .equals(currentOfficer.getId())) {
            throw new AccessDeniedException(
                    "You are not authorized to modify this assignment"
            );
        }
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment submitAssignment(
            UUID assignmentId,
            String completionReport
    ) {
        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found"));

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        verifyAssignmentOwner(assignment, changedBy);

        if (!"IN_PROGRESS".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Only in-progress assignments can be submitted");
        }

        if (completionReport == null || completionReport.isBlank()) {
            throw new IllegalArgumentException(
                    "Completion report is required");
        }

        Map<String, Object> oldValues = Map.of(
                "status", assignment.getStatus(),
                "completionReport", assignment.getCompletionReport() == null
                        ? "" : assignment.getCompletionReport()
        );

        assignment.setCompletionReport(completionReport.trim());
        assignment.setSubmittedAt(OffsetDateTime.now());
        assignment.setReviewRemarks(null);
        assignment.setStatus("SUBMITTED");

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "ASSIGNMENT_SUBMITTED",
                oldValues,
                Map.of(
                        "status", saved.getStatus(),
                        "completionReport", saved.getCompletionReport(),
                        "submittedAt", saved.getSubmittedAt().toString()
                ),
                "Officer submitted completion report for review"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment approveCompletion(
            UUID assignmentId,
            String remarks
    ) {
        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found"));

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();

        verifySupervisorOrAdmin();

        if (assignment.getOfficer().getId().equals(changedBy.getId())) {
            throw new AccessDeniedException(
                    "An officer cannot approve their own assignment");
        }

        if (!"SUBMITTED".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Only submitted assignments can be approved");
        }

        Map<String, Object> oldValues = Map.of(
                "status", assignment.getStatus(),
                "reviewRemarks", assignment.getReviewRemarks() == null
                        ? "" : assignment.getReviewRemarks()
        );

        assignment.setStatus("COMPLETED");
        assignment.setCompletedAt(OffsetDateTime.now());
        assignment.setReviewRemarks(remarks);

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "COMPLETION_APPROVED",
                oldValues,
                Map.of(
                        "status", saved.getStatus(),
                        "reviewRemarks", remarks == null ? "" : remarks,
                        "completedAt", saved.getCompletedAt().toString()
                ),
                "Supervisor approved task completion"
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment requestCorrection(
            UUID assignmentId,
            String remarks
    ) {
        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found"));

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();

        verifySupervisorOrAdmin();

        if (assignment.getOfficer().getId().equals(changedBy.getId())) {
            throw new AccessDeniedException(
                    "An officer cannot review their own assignment");
        }

        if (!"SUBMITTED".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Only submitted assignments can be returned for correction");
        }

        if (remarks == null || remarks.isBlank()) {
            throw new IllegalArgumentException(
                    "Correction remarks are required");
        }

        String oldStatus = assignment.getStatus();

        assignment.setStatus("IN_PROGRESS");
        assignment.setReviewRemarks(remarks.trim());

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "CORRECTION_REQUESTED",
                Map.of("status", oldStatus),
                Map.of(
                        "status", saved.getStatus(),
                        "reviewRemarks", saved.getReviewRemarks()
                ),
                remarks.trim()
        );

        return saved;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment updateAssignment(
            UUID assignmentId,
            String responsibility,
            String instructions
    ) {

        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found")
                );

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        verifyAssignmentManager(assignment, changedBy);

        if ("COMPLETED".equals(assignment.getStatus())
                || "CANCELLED".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Cannot update a completed or cancelled assignment"
            );
        }

        if (responsibility == null || responsibility.isBlank()) {
            throw new IllegalArgumentException(
                    "Responsibility is required"
            );
        }

        Map<String, Object> oldValues = Map.of(
                "responsibility", assignment.getResponsibility(),
                "instructions", assignment.getInstructions() == null
                        ? "" : assignment.getInstructions()
        );

        assignment.setResponsibility(responsibility.trim());
        assignment.setInstructions(instructions);

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "ASSIGNMENT_UPDATED",
                oldValues,
                Map.of(
                        "responsibility", saved.getResponsibility(),
                        "instructions", saved.getInstructions() == null
                                ? "" : saved.getInstructions()
                ),
                "Assignment details updated"
        );

        return saved;
    }
	
    
@Transactional(transactionManager = "postgresTransactionManager")
public TaskAssignment reassignOfficer(
        UUID assignmentId,
        UUID newOfficerId,
        String responsibility,
        String instructions,
        String reason
) {
    if (responsibility == null || responsibility.isBlank()) {
        throw new IllegalArgumentException("Responsibility is required");
    }

    if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("Reassignment reason is required");
    }

    TaskAssignment oldAssignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() ->
                    new EntityNotFoundException("Assignment not found"));

    OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();

    verifyAssignmentManager(oldAssignment, changedBy);

    if ("COMPLETED".equals(oldAssignment.getStatus())
            || "CANCELLED".equals(oldAssignment.getStatus())) {
        throw new IllegalStateException(
                "Completed or cancelled assignments cannot be reassigned"
        );
    }

    OfficerProfile newOfficer = officerRepository.findById(newOfficerId)
            .orElseThrow(() ->
                    new EntityNotFoundException("New officer not found"));

    if (oldAssignment.getOfficer().getId().equals(newOfficerId)) {
        throw new IllegalArgumentException(
                "New officer is already assigned to this assignment"
        );
    }

    if (assignmentRepository.existsByTaskIdAndOfficerId(
            oldAssignment.getTask().getId(), newOfficerId)) {
        throw new IllegalArgumentException(
                "New officer is already assigned to this task"
        );
    }

    String oldStatus = oldAssignment.getStatus();
    String oldOfficerId = oldAssignment.getOfficer().getId().toString();

    // Cancel the old assignment while preserving its record.
    oldAssignment.setStatus("CANCELLED");

    TaskAssignment cancelledAssignment =
            assignmentRepository.save(oldAssignment);

    auditService.record(
            oldAssignment.getTask(),
            cancelledAssignment,
            changedBy,
            "ASSIGNMENT_REASSIGNED",
            Map.of(
                    "officerId", oldOfficerId,
                    "status", oldStatus
            ),
            Map.of(
                    "officerId", oldOfficerId,
                    "status", "CANCELLED",
                    "newOfficerId", newOfficerId.toString()
            ),
            reason.trim()
    );

    // Create a separate assignment for the new officer.
    TaskAssignment newAssignment = new TaskAssignment();

    newAssignment.setTask(oldAssignment.getTask());
    newAssignment.setOfficer(newOfficer);
    newAssignment.setAssignedBy(changedBy);
    newAssignment.setResponsibility(responsibility.trim());
    newAssignment.setInstructions(instructions);
    newAssignment.setStatus("ASSIGNED");

    TaskAssignment savedNewAssignment =
            assignmentRepository.save(newAssignment);

    auditService.record(
            savedNewAssignment.getTask(),
            savedNewAssignment,
            changedBy,
            "OFFICER_ASSIGNED",
            null,
            Map.of(
                    "officerId", newOfficerId.toString(),
                    "responsibility", responsibility.trim(),
                    "status", "ASSIGNED",
                    "reassignedFrom", oldOfficerId
            ),
            "New assignment created through reassignment"
    );

    return savedNewAssignment;
}

    @Transactional(transactionManager = "postgresTransactionManager")
    public TaskAssignment cancelAssignment(
            UUID assignmentId,
            String reason
    ) {

        TaskAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found")
                );

        OfficerProfile changedBy = currentOfficerService.getCurrentOfficer();
        verifyAssignmentManager(assignment, changedBy);

        if ("COMPLETED".equals(assignment.getStatus())
                || "CANCELLED".equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Assignment is already completed or cancelled"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Cancellation reason is required"
            );
        }

        String oldStatus = assignment.getStatus();

        assignment.setStatus("CANCELLED");

        TaskAssignment saved = assignmentRepository.save(assignment);

        auditService.record(
                assignment.getTask(),
                saved,
                changedBy,
                "ASSIGNMENT_CANCELLED",
                Map.of("status", oldStatus),
                Map.of("status", "CANCELLED"),
                reason.trim()
        );

        return saved;
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
                    "Only supervisors or administrators can review assignments"
            );
        }
    }

    private void verifyAssignmentManager(
            TaskAssignment assignment,
            OfficerProfile currentOfficer
    ) {
        boolean isAssigner = assignment.getAssignedBy().getId()
                .equals(currentOfficer.getId());

        if (!isAssigner) {
            verifySupervisorOrAdmin();
        }
    }
}
