package com.substring.auth.app.tracking.controller;

import com.substring.auth.app.tracking.dto.LocationUpdateRequest;
import com.substring.auth.app.tracking.dto.OfficerLiveLocationResponse;
import com.substring.auth.app.tracking.service.LocationTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tracking")
public class LocationTrackingController {

    private final LocationTrackingService trackingService;

    public LocationTrackingController(LocationTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping({"/update", "/locations"})
    public ResponseEntity<OfficerLiveLocationResponse> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        OfficerLiveLocationResponse response = trackingService.recordLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/teams/{teamId}/live", "/teams/{teamId}"})
    public ResponseEntity<List<OfficerLiveLocationResponse>> getLiveLocationsForTeam(
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(trackingService.getLiveLocationsForTeam(teamId));
    }

    @GetMapping({"/officers/{officerId}/latest", "/officers/{officerId}"})
    public ResponseEntity<OfficerLiveLocationResponse> getOfficerLatestLocation(
            @PathVariable UUID officerId
    ) {
        return trackingService.getOfficerLatestLocation(officerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
