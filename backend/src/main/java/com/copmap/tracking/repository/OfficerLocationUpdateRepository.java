package com.copmap.tracking.repository;

import com.copmap.tracking.entity.OfficerLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficerLocationUpdateRepository
        extends JpaRepository<OfficerLocationUpdate, UUID> {

    Optional<OfficerLocationUpdate> findFirstByOfficer_IdOrderByRecordedAtDesc(UUID officerId);

    List<OfficerLocationUpdate> findAllByOfficer_IdAndRecordedAtAfterOrderByRecordedAtDesc(
            UUID officerId,
            OffsetDateTime after
    );

    @Query("SELECT o FROM OfficerLocationUpdate o WHERE o.officer.id IN :officerIds "
            + "AND o.recordedAt = (SELECT MAX(sub.recordedAt) FROM OfficerLocationUpdate sub WHERE sub.officer.id = o.officer.id)")
    List<OfficerLocationUpdate> findLatestLocationsForOfficers(@Param("officerIds") List<UUID> officerIds);
}
