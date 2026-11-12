package com.substring.auth.app.officer.service;

import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentOfficerService {

    private final OfficerProfileRepository officerRepository;

    public CurrentOfficerService(
            OfficerProfileRepository officerRepository
    ) {
        this.officerRepository = officerRepository;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public OfficerProfile getCurrentOfficer() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user not found"
            );
        }

        return officerRepository.findByMysqlUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Officer profile not found for authenticated user"
                ));
    }
}
