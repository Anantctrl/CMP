package com.substring.auth.app.tracking.dto;

import com.substring.auth.app.tracking.entity.OfficerLocationUpdate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OfficerLiveLocationResponse(
        UUID officerId,
        String officerName,
        String designation,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        OffsetDateTime recordedAt,
        OffsetDateTime receivedAt,
        String freshnessStatus
) {
    public static OfficerLiveLocationResponse from(OfficerLocationUpdate loc) {
        OffsetDateTime now = OffsetDateTime.now();
        Duration diff = Duration.between(loc.getRecordedAt(), now).abs();
        String freshness;
        if (diff.getSeconds() < 300) {
            freshness = "FRESH";
        } else if (diff.getSeconds() < 900) {
            freshness = "DEGRADED";
        } else {
            freshness = "STALE";
        }

        String name = loc.getOfficer().getOfficerName();
        if (name == null || name.isBlank()) {
            name = loc.getOfficer().getDesignation();
        }

        return new OfficerLiveLocationResponse(
                loc.getOfficer().getId(),
                name,
                loc.getOfficer().getDesignation(),
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getAccuracyMeters(),
                loc.getRecordedAt(),
                loc.getReceivedAt(),
                freshness
        );
    }
}
