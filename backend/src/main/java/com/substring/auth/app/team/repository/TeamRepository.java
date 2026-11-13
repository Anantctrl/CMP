package com.substring.auth.app.team.repository;

import com.substring.auth.app.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    boolean existsByName(String name);
    Optional<Team> findByName(String name);
}
