
package com.substring.auth.app.operations.common.entity;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.operations.common.enums.OperationAssignmentStatus;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "operation_assignments")
public class OperationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private OperationLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id", nullable = false)
    private OfficerProfile officer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private OfficerProfile assignedBy;

    @Column(nullable = false, length = 150)
    private String responsibility;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationAssignmentStatus status =
            OperationAssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completion_report", columnDefinition = "TEXT")
    private String completionReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        assignedAt = now;
        createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public OperationLocation getLocation() {
        return location;
    }

    public void setLocation(OperationLocation location) {
        this.location = location;
    }

    public OfficerProfile getOfficer() {
        return officer;
    }

    public void setOfficer(OfficerProfile officer) {
        this.officer = officer;
    }

    public OfficerProfile getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(OfficerProfile assignedBy) {
        this.assignedBy = assignedBy;
    }

    public String getResponsibility() {
        return responsibility;
    }

    public void setResponsibility(String responsibility) {
        this.responsibility = responsibility;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public OperationAssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(OperationAssignmentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getCompletionReport() {
        return completionReport;
    }

    public void setCompletionReport(String completionReport) {
        this.completionReport = completionReport;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
