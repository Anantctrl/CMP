package com.copmap.auth.repositories;

import com.copmap.auth.entities.Provider;
import com.copmap.auth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
    Optional<User> findByProviderId(String providerId);
}
