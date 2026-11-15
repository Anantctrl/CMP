package com.copmap.operations.common.controller;

import com.copmap.operations.common.dto.AssignOperationOfficerRequest;
import com.copmap.operations.common.dto.CancelOperationAssignmentRequest;
import com.copmap.operations.common.dto.CompleteOperationAssignmentRequest;
import com.copmap.operations.common.dto.OperationAssignmentResponse;
import com.copmap.operations.common.entity.OperationAssignment;
import com.copmap.operations.common.service.OperationAssignmentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationAssignmentController {

    private final OperationAssignmentService assignmentService;

    public OperationAssignmentController(
            OperationAssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
    }

    // Supervisor/Admin assigns an officer to an operation location.
    @PostMapping("/locations/{locationId}/assignments")
    public ResponseEntity<OperationAssignmentResponse> assignOfficer(
            @PathVariable UUID locationId,
            @Valid @RequestBody AssignOperationOfficerRequest request
    ) {
        OperationAssignment assignment =
                assignmentService.assignOfficer(
                        locationId,
                        request.officerId(),
                        request.responsibility(),
                        request.instructions()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(OperationAssignmentResponse.from(assignment));
    }

    // Logged-in officer views their own assignments and history.
    @GetMapping("/my-assignments")
    public ResponseEntity<List<OperationAssignmentResponse>> getMyAssignments() {

        List<OperationAssignmentResponse> response =
                assignmentService.getMyAssignments()
                        .stream()
                        .map(OperationAssignmentResponse::from)
                        .toList();

        return ResponseEntity.ok(response);
    }

    // View one assignment with ownership/role authorization.
    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<OperationAssignmentResponse> getAssignmentById(
            @PathVariable UUID assignmentId
    ) {
        return ResponseEntity.ok(
                OperationAssignmentResponse.from(
                        assignmentService.getAssignmentById(assignmentId)
                )
        );
    }

    // View assignments belonging to an operation.
    @GetMapping("/{operationId}/assignments")
    public ResponseEntity<List<OperationAssignmentResponse>> getAssignmentsByOperation(
            @PathVariable UUID operationId
    ) {
        List<OperationAssignmentResponse> response =
                assignmentService.getAssignmentsByOperation(operationId)
                        .stream()
                        .map(OperationAssignmentResponse::from)
                        .toList();

        return ResponseEntity.ok(response);
    }

    // Officer acknowledges their assignment.
    @PatchMapping("/assignments/{assignmentId}/acknowledge")
    public ResponseEntity<OperationAssignmentResponse> acknowledgeAssignment(
            @PathVariable UUID assignmentId
    ) {
        return ResponseEntity.ok(
                OperationAssignmentResponse.from(
                        assignmentService.acknowledgeAssignment(assignmentId)
                )
        );
    }

    // Officer starts their acknowledged assignment.
    @PatchMapping("/assignments/{assignmentId}/start")
    public ResponseEntity<OperationAssignmentResponse> startAssignment(
            @PathVariable UUID assignmentId
    ) {
        return ResponseEntity.ok(
                OperationAssignmentResponse.from(
                        assignmentService.startAssignment(assignmentId)
                )
        );
    }

    // Officer submits the completion report.
    @PatchMapping("/assignments/{assignmentId}/complete")
    public ResponseEntity<OperationAssignmentResponse> completeAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody CompleteOperationAssignmentRequest request
    ) {
        return ResponseEntity.ok(
                OperationAssignmentResponse.from(
                        assignmentService.submitCompletion(
                                assignmentId,
                                request.completionReport()
                        )
                )
        );
    }

    // Supervisor/Admin cancels an assignment.
    @PatchMapping("/assignments/{assignmentId}/cancel")
    public ResponseEntity<OperationAssignmentResponse> cancelAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody CancelOperationAssignmentRequest request
    ) {
        return ResponseEntity.ok(
                OperationAssignmentResponse.from(
                        assignmentService.cancelAssignment(
                                assignmentId,
                                request.reason()
                        )
                )
        );
    }
}
