package com.substring.auth.app.officer.repository;

import com.substring.auth.app.officer.entity.OfficerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfficerProfileRepository
        extends JpaRepository<OfficerProfile, UUID> {

    Optional<OfficerProfile> findByMysqlUserId(UUID mysqlUserId);
}
