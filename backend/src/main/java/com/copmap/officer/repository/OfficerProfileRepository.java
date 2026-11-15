package com.copmap.officer.repository;

import com.copmap.officer.entity.OfficerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfficerProfileRepository
        extends JpaRepository<OfficerProfile, UUID> {

    Optional<OfficerProfile> findByMysqlUserId(UUID mysqlUserId);
}
