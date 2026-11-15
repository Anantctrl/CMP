package com.copmap.operations.common.entity;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.team.entity.Team;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "operation_approval_requests")
public class OperationApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private Operation operation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private OfficerProfile requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_team_id", nullable = false)
    private Team targetTeam;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private OfficerProfile reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "request_remarks", columnDefinition = "TEXT")
    private String requestRemarks;

    @Column(name = "review_remarks", columnDefinition = "TEXT")
    private String reviewRemarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public OfficerProfile getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(OfficerProfile requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Team getTargetTeam() {
        return targetTeam;
    }

    public void setTargetTeam(Team targetTeam) {
        this.targetTeam = targetTeam;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OfficerProfile getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(OfficerProfile reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getRequestRemarks() {
        return requestRemarks;
    }

    public void setRequestRemarks(String requestRemarks) {
        this.requestRemarks = requestRemarks;
    }

    public String getReviewRemarks() {
        return reviewRemarks;
    }

    public void setReviewRemarks(String reviewRemarks) {
        this.reviewRemarks = reviewRemarks;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
