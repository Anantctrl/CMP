package com.substring.auth.app.operations.common.controller;

import com.substring.auth.app.operations.common.dto.*;
import com.substring.auth.app.operations.common.entity.Operation;
import com.substring.auth.app.operations.common.enums.OperationStatus;
import com.substring.auth.app.operations.common.enums.OperationType;
import com.substring.auth.app.operations.common.service.OperationApprovalService;
import com.substring.auth.app.operations.common.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationController {

    private final OperationService operationService;
    private final OperationApprovalService approvalService;

    public OperationController(
            OperationService operationService,
            OperationApprovalService approvalService
    ) {
        this.operationService = operationService;
        this.approvalService = approvalService;
    }

    @PostMapping
    public ResponseEntity<OperationResponse> createOperation(
            @Valid @RequestBody CreateOperationRequest request
    ) {
        Operation operation = operationService.createOperation(
                request.type(),
                request.title(),
                request.description(),
                request.teamIds(),
                request.requestRemarks()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(operation));
    }

    @GetMapping
    public ResponseEntity<List<OperationResponse>> getOperations(
            @RequestParam(required = false) OperationType type,
            @RequestParam(required = false) OperationStatus status
    ) {
        List<Operation> operations;

        if (type != null && status != null) {
            operations = operationService.getOperationsByTypeAndStatus(type, status);
        } else if (type != null) {
            operations = operationService.getOperationsByType(type);
        } else if (status != null) {
            operations = operationService.getOperationsByStatus(status);
        } else {
            operations = operationService.getAllOperations();
        }

        return ResponseEntity.ok(
                operations.stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<OperationResponse> getOperation(
            @PathVariable UUID operationId
    ) {
        return ResponseEntity.ok(toResponse(operationService.getOperation(operationId)));
    }

    @PutMapping("/{operationId}/schedule")
    public ResponseEntity<OperationResponse> scheduleOperation(
            @PathVariable UUID operationId,
            @Valid @RequestBody ScheduleOperationRequest request
    ) {
        Operation operation = operationService.scheduleOperation(
                operationId,
                request.plannedStartAt(),
                request.plannedEndAt(),
                request.remarks()
        );

        return ResponseEntity.ok(toResponse(operation));
    }

    @PatchMapping("/{operationId}/status")
    public ResponseEntity<OperationResponse> changeOperationStatus(
            @PathVariable UUID operationId,
            @Valid @RequestBody ChangeOperationStatusRequest request
    ) {
        Operation operation = operationService.changeOperationStatus(
                operationId,
                request.status(),
                request.remarks()
        );

        return ResponseEntity.ok(toResponse(operation));
    }

    // Cross-team operation approval endpoints
    @GetMapping("/approval-requests")
    public ResponseEntity<List<OperationApprovalResponse>> getApprovalRequests(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(approvalService.getApprovalRequests(status));
    }

    @PostMapping("/approval-requests/{id}/approve")
    public ResponseEntity<OperationApprovalResponse> approveRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewApprovalRequest request
    ) {
        String remarks = request != null ? request.remarks() : null;
        return ResponseEntity.ok(approvalService.approveRequest(id, remarks));
    }

    @PostMapping("/approval-requests/{id}/reject")
    public ResponseEntity<OperationApprovalResponse> rejectRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewApprovalRequest request
    ) {
        String remarks = request != null ? request.remarks() : null;
        return ResponseEntity.ok(approvalService.rejectRequest(id, remarks));
    }

    private OperationResponse toResponse(Operation operation) {
        List<UUID> teams = operationService.getParticipatingTeamIds(operation.getId());
        boolean pending = operationService.hasPendingApproval(operation.getId());
        return OperationResponse.from(operation, teams, pending);
    }
}
