
package com.copmap.operations.common.entity;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.operations.common.enums.OperationStatus;
import com.copmap.operations.common.enums.OperationType;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "operations")
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "planned_start_at")
    private OffsetDateTime plannedStartAt;

    @Column(name = "planned_end_at")
    private OffsetDateTime plannedEndAt;

    @Column(name = "actual_start_at")
    private OffsetDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private OffsetDateTime actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationStatus status = OperationStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private OfficerProfile createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getPlannedStartAt() {
        return plannedStartAt;
    }

    public void setPlannedStartAt(OffsetDateTime plannedStartAt) {
        this.plannedStartAt = plannedStartAt;
    }

    public OffsetDateTime getPlannedEndAt() {
        return plannedEndAt;
    }

    public void setPlannedEndAt(OffsetDateTime plannedEndAt) {
        this.plannedEndAt = plannedEndAt;
    }

    public OffsetDateTime getActualStartAt() {
        return actualStartAt;
    }

    public void setActualStartAt(OffsetDateTime actualStartAt) {
        this.actualStartAt = actualStartAt;
    }

    public OffsetDateTime getActualEndAt() {
        return actualEndAt;
    }

    public void setActualEndAt(OffsetDateTime actualEndAt) {
        this.actualEndAt = actualEndAt;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public OfficerProfile getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(OfficerProfile createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
