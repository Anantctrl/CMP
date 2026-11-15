
package com.copmap.operations.common.controller;

import com.copmap.operations.common.dto.CreateOperationLocationRequest;
import com.copmap.operations.common.dto.OperationLocationResponse;
import com.copmap.operations.common.dto.UpdateOperationLocationRequest;
import com.copmap.operations.common.service.OperationLocationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationLocationController {

    private final OperationLocationService locationService;

    public OperationLocationController(
            OperationLocationService locationService
    ) {
        this.locationService = locationService;
    }

    @PostMapping("/{operationId}/locations")
    public ResponseEntity<OperationLocationResponse> createLocation(
            @PathVariable UUID operationId,
            @Valid @RequestBody CreateOperationLocationRequest request
    ) {

        OperationLocationResponse response =
                locationService.createLocation(operationId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{operationId}/locations")
    public ResponseEntity<List<OperationLocationResponse>> getLocations(
            @PathVariable UUID operationId
    ) {

        return ResponseEntity.ok(
                locationService.getLocations(operationId)
        );
    }

    @GetMapping("/locations/{locationId}")
    public ResponseEntity<OperationLocationResponse> getLocation(
            @PathVariable UUID locationId
    ) {

        return ResponseEntity.ok(
                locationService.getLocationById(locationId)
        );
    }

    @PutMapping("/locations/{locationId}")
    public ResponseEntity<OperationLocationResponse> updateLocation(
            @PathVariable UUID locationId,
            @Valid @RequestBody UpdateOperationLocationRequest request
    ) {

        return ResponseEntity.ok(
                locationService.updateLocation(locationId, request)
        );
    }

    @DeleteMapping("/locations/{locationId}")
    public ResponseEntity<Void> deleteLocation(
            @PathVariable UUID locationId,
            @RequestParam(required = false) String remarks
    ) {

        locationService.deleteLocation(locationId, remarks);

        return ResponseEntity.noContent().build();
    }
}
