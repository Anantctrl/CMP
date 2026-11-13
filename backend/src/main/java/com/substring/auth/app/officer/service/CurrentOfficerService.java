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

    @Transactional(transactionManager = "postgresTransactionManager")
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

        OfficerProfile profile = officerRepository.findByMysqlUserId(user.getId())
                .orElseGet(() -> {
                    OfficerProfile newProfile = new OfficerProfile();
                    newProfile.setMysqlUserId(user.getId());
                    newProfile.setOfficerName(user.getName());
                    String desig = (user.getPolicePost() != null && !user.getPolicePost().isBlank())
                            ? user.getPolicePost().trim()
                            : "Officer";
                    newProfile.setDesignation(desig);
                    return officerRepository.save(newProfile);
                });

        if ((profile.getOfficerName() == null || profile.getOfficerName().isBlank()) && user.getName() != null) {
            profile.setOfficerName(user.getName());
            profile = officerRepository.save(profile);
        }

        return profile;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public OfficerProfile getOfficerByMysqlUserId(java.util.UUID mysqlUserId) {
        return officerRepository.findByMysqlUserId(mysqlUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Officer profile not found for user: " + mysqlUserId
                ));
    }
}
