package com.copmap.tracking.entity;

import com.copmap.officer.entity.OfficerProfile;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "officer_location_updates",
        indexes = {
                @Index(
                        name = "idx_officer_location_recorded",
                        columnList = "officer_id, recorded_at"
                )
        }
)
public class OfficerLocationUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id", nullable = false)
    private OfficerProfile officer;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
        if (recordedAt == null) {
            recordedAt = receivedAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public OfficerProfile getOfficer() {
        return officer;
    }

    public void setOfficer(OfficerProfile officer) {
        this.officer = officer;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(OffsetDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
