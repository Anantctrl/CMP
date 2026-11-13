package com.substring.auth.app.operations.common.controller;

import com.substring.auth.app.operations.common.dto.OperationAuditResponse;
import com.substring.auth.app.operations.common.service.OperationAuditService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationAuditController {

    private final OperationAuditService auditService;

    public OperationAuditController(
            OperationAuditService auditService
    ) {
        this.auditService = auditService;
    }

    // Supervisor/Admin views the complete operation audit history.
    @GetMapping("/{operationId}/audit-history")
    public ResponseEntity<List<OperationAuditResponse>> getOperationAuditHistory(
            @PathVariable UUID operationId
    ) {

        List<OperationAuditResponse> response =
                auditService.getOperationAuditHistory(operationId)
                        .stream()
                        .map(OperationAuditResponse::from)
                        .toList();

        return ResponseEntity.ok(response);
    }
}
