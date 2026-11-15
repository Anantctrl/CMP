
package com.copmap.operations.common.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.operations.common.dto.CreateOperationLocationRequest;
import com.copmap.operations.common.dto.OperationLocationResponse;
import com.copmap.operations.common.dto.UpdateOperationLocationRequest;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationLocation;
import com.copmap.operations.common.repository.OperationAssignmentRepository;
import com.copmap.operations.common.repository.OperationAuditLogRepository;
import com.copmap.operations.common.repository.OperationLocationRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationLocationService {

    private final OperationService operationService;
    private final OperationLocationRepository locationRepository;
    private final OperationAssignmentRepository assignmentRepository;
    private final OperationAuditLogRepository auditLogRepository;
    private final CurrentOfficerService currentOfficerService;
    private final OperationAuditService auditService;

    public OperationLocationService(
            OperationService operationService,
            OperationLocationRepository locationRepository,
            OperationAssignmentRepository assignmentRepository,
            OperationAuditLogRepository auditLogRepository,
            CurrentOfficerService currentOfficerService,
            OperationAuditService auditService
    ) {
        this.operationService = operationService;
        this.locationRepository = locationRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.currentOfficerService = currentOfficerService;
        this.auditService = auditService;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationLocationResponse createLocation(
            UUID operationId,
            CreateOperationLocationRequest request
    ) {

        verifySupervisorOrAdmin();

        Operation operation = operationService.getOperation(operationId);

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        OperationLocation location = new OperationLocation();
        location.setOperation(operation);

        applyLocationDetails(
                location,
                request.name(),
                request.description(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.requiredManpower(),
                request.sequenceOrder()
        );

        OperationLocation savedLocation =
                locationRepository.save(location);

        auditService.record(
                operation,
                savedLocation,
                null,
                changedBy,
                "OPERATION_LOCATION_CREATED",
                null,
                locationValues(savedLocation),
                "Operation location created"
        );

        return toResponse(savedLocation);
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public OperationLocationResponse updateLocation(
            UUID locationId,
            UpdateOperationLocationRequest request
    ) {

        verifySupervisorOrAdmin();

        OperationLocation location = getLocation(locationId);

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        Map<String, Object> oldValues = locationValues(location);

        applyLocationDetails(
                location,
                request.name(),
                request.description(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.requiredManpower(),
                request.sequenceOrder()
        );

        OperationLocation savedLocation =
                locationRepository.save(location);

        auditService.record(
                savedLocation.getOperation(),
                savedLocation,
                null,
                changedBy,
                "OPERATION_LOCATION_UPDATED",
                oldValues,
                locationValues(savedLocation),
                "Operation location updated"
        );

        return toResponse(savedLocation);
    }

    @Transactional(readOnly = true,
            transactionManager = "postgresTransactionManager")
    public List<OperationLocationResponse> getLocations(
            UUID operationId
    ) {

        Operation operation = operationService.getOperation(operationId);

        verifyOperationReadAccess(operation);

        return locationRepository
                .findAllByOperation_IdOrderBySequenceOrderAsc(operationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true,
            transactionManager = "postgresTransactionManager")
    public OperationLocationResponse getLocationById(
            UUID locationId
    ) {

        OperationLocation location = getLocation(locationId);

        verifyOperationReadAccess(location.getOperation());

        return toResponse(location);
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public void deleteLocation(UUID locationId, String remarks) {

        verifySupervisorOrAdmin();

        OperationLocation location = getLocation(locationId);

        UUID id = location.getId();

        if (assignmentRepository.existsByLocation_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete location because assignment history exists"
            );
        }

        if (auditLogRepository.existsByLocation_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete location because audit history exists"
            );
        }

        OfficerProfile changedBy =
                currentOfficerService.getCurrentOfficer();

        Operation operation = location.getOperation();

        Map<String, Object> oldValues = locationValues(location);

        auditService.record(
                operation,
                null,
                null,
                changedBy,
                "OPERATION_LOCATION_DELETED",
                oldValues,
                null,
                remarks == null || remarks.isBlank()
                        ? "Operation location deleted"
                        : remarks.trim()
        );

        locationRepository.delete(location);
    }

    private OperationLocation getLocation(UUID locationId) {

        return locationRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Operation location not found: " + locationId
                ));
    }

    private void applyLocationDetails(
            OperationLocation location,
            String name,
            String description,
            String address,
            Double latitude,
            Double longitude,
            Integer requiredManpower,
            Integer sequenceOrder
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Location name is required"
            );
        }

        if (name.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Location name cannot exceed 150 characters"
            );
        }

        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90"
            );
        }

        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180"
            );
        }

        if (requiredManpower == null || requiredManpower < 0) {
            throw new IllegalArgumentException(
                    "Required manpower cannot be negative"
            );
        }

        if (sequenceOrder == null || sequenceOrder < 0) {
            throw new IllegalArgumentException(
                    "Sequence order cannot be negative"
            );
        }

        if (address != null && address.length() > 255) {
            throw new IllegalArgumentException(
                    "Address cannot exceed 255 characters"
            );
        }

        location.setName(name.trim());
        location.setDescription(description);
        location.setAddress(address);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setRequiredManpower(requiredManpower);
        location.setSequenceOrder(sequenceOrder);
    }

    private Map<String, Object> locationValues(
            OperationLocation location
    ) {

        Map<String, Object> values = new HashMap<>();

        values.put("id", location.getId().toString());
        values.put("name", location.getName());
        values.put("description", location.getDescription());
        values.put("address", location.getAddress());
        values.put("latitude", location.getLatitude());
        values.put("longitude", location.getLongitude());
        values.put("requiredManpower", location.getRequiredManpower());
        values.put("sequenceOrder", location.getSequenceOrder());

        return values;
    }

    private OperationLocationResponse toResponse(
            OperationLocation location
    ) {

        return new OperationLocationResponse(
                location.getId(),
                location.getOperation().getId(),
                location.getName(),
                location.getDescription(),
                location.getAddress(),
                location.getLatitude(),
                location.getLongitude(),
                location.getRequiredManpower(),
                location.getSequenceOrder(),
                location.getCreatedAt()
        );
    }

    private void verifyOperationReadAccess(Operation operation) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        boolean supervisorOrAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("Supervisor")
                                || authority.getAuthority().equals("Administrator")
                );

        if (supervisorOrAdmin) {
            return;
        }

        OfficerProfile currentOfficer =
                currentOfficerService.getCurrentOfficer();

        boolean assigned = assignmentRepository
                .findAllByOfficer_Id(currentOfficer.getId())
                .stream()
                .anyMatch(assignment ->
                        assignment.getLocation().getOperation().getId()
                                .equals(operation.getId())
                );

        if (!assigned) {
            throw new AccessDeniedException(
                    "You are not assigned to this operation"
            );
        }
    }

    private void verifySupervisorOrAdmin() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        boolean authorized = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("Supervisor")
                                || authority.getAuthority().equals("Administrator")
                );

        if (!authorized) {
            throw new AccessDeniedException(
                    "Only supervisors or administrators can manage operation locations"
            );
        }
    }
}
