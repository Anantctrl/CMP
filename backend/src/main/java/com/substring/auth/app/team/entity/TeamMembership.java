package com.substring.auth.app.team.entity;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.team.enums.TeamRole;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "team_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_team_officer",
                        columnNames = {"team_id", "officer_id"}
                )
        }
)
public class TeamMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id", nullable = false)
    private OfficerProfile officer;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false, length = 50)
    private TeamRole teamRole = TeamRole.FIELD_OPERATOR;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        joinedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public OfficerProfile getOfficer() {
        return officer;
    }

    public void setOfficer(OfficerProfile officer) {
        this.officer = officer;
    }

    public TeamRole getTeamRole() {
        return teamRole;
    }

    public void setTeamRole(TeamRole teamRole) {
        this.teamRole = teamRole;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
