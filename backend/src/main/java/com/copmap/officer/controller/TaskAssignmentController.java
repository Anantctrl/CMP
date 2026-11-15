package com.copmap.officer.controller;

import com.copmap.officer.dto.*;
import com.copmap.officer.entity.TaskAssignment;
import com.copmap.officer.service.TaskAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class TaskAssignmentController {

    private final TaskAssignmentService assignmentService;

    public TaskAssignmentController(
            TaskAssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/tasks/{taskId}/assign")
    public ResponseEntity<AssignmentResponse> assignOfficer(
            @PathVariable UUID taskId,
            @Valid @RequestBody AssignOfficerRequest request
    ) {
        TaskAssignment assignment = assignmentService.assignOfficer(
                taskId,
                request.officerId(),
                request.responsibility(),
                request.instructions()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(assignment));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsByTask(
            @PathVariable UUID taskId
    ) {
        List<TaskAssignment> assignments = assignmentService.getAssignmentsByTaskId(taskId);
        return ResponseEntity.ok(assignments.stream().map(this::toResponse).toList());
    }

    @GetMapping("/my-assignments")
    public ResponseEntity<List<AssignmentResponse>> getMyAssignments() {
        List<TaskAssignment> assignments = assignmentService.getMyAssignments();
        return ResponseEntity.ok(assignments.stream().map(this::toResponse).toList());
    }

    @PostMapping("/{id}/reassign")
    public ResponseEntity<AssignmentResponse> reassignOfficer(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignOfficerRequest request
    ) {
        TaskAssignment assignment = assignmentService.reassignOfficer(
                id,
                request.officerId(),
                request.responsibility(),
                request.instructions(),
                request.reason()
        );

        return ResponseEntity.ok(toResponse(assignment));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<AssignmentResponse> startAssignment(
            @PathVariable UUID id
    ) {
        TaskAssignment assignment =
                assignmentService.startAssignment(id);

        return ResponseEntity.ok(toResponse(assignment));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<AssignmentResponse> submitAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAssignmentRequest request
    ) {
        TaskAssignment assignment =
                assignmentService.submitAssignment(
                        id,
                        request.completionReport()
                );

        return ResponseEntity.ok(toResponse(assignment));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AssignmentResponse> approveCompletion(
            @PathVariable UUID id,
            @RequestBody ReviewAssignmentRequest request
    ) {
        TaskAssignment assignment =
                assignmentService.approveCompletion(
                        id,
                        request.remarks()
                );

        return ResponseEntity.ok(toResponse(assignment));
    }

    @PostMapping("/{id}/request-correction")
    public ResponseEntity<AssignmentResponse> requestCorrection(
            @PathVariable UUID id,
            @RequestBody ReviewAssignmentRequest request
    ) {
        TaskAssignment assignment =
                assignmentService.requestCorrection(
                        id,
                        request.remarks()
                );

        return ResponseEntity.ok(toResponse(assignment));
    }

    private AssignmentResponse toResponse(TaskAssignment assignment) {
        String officerName = assignment.getOfficer() != null ? assignment.getOfficer().getOfficerName() : null;
        if (officerName == null || officerName.isBlank()) {
            officerName = assignment.getOfficer() != null ? assignment.getOfficer().getDesignation() : "Officer";
        }
        String designation = assignment.getOfficer() != null ? assignment.getOfficer().getDesignation() : null;

        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTask().getId(),
                assignment.getOfficer().getId(),
                officerName,
                designation,
                assignment.getResponsibility(),
                assignment.getInstructions(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getStartedAt(),
                assignment.getSubmittedAt(),
                assignment.getCompletedAt(),
                assignment.getCompletionReport(),
                assignment.getReviewRemarks()
        );
    }
}
