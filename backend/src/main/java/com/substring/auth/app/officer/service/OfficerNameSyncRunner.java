package com.substring.auth.app.officer.service;

import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class OfficerNameSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(OfficerNameSyncRunner.class);

    private final OfficerProfileRepository officerRepository;
    private final UserRepository userRepository;

    public OfficerNameSyncRunner(
            OfficerProfileRepository officerRepository,
            UserRepository userRepository
    ) {
        this.officerRepository = officerRepository;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(transactionManager = "postgresTransactionManager")
    public void syncOfficerNames() {
        try {
            List<OfficerProfile> profiles = officerRepository.findAll();
            int updatedCount = 0;
            for (OfficerProfile profile : profiles) {
                if (profile.getOfficerName() == null || profile.getOfficerName().isBlank()) {
                    Optional<User> userOpt = userRepository.findById(profile.getMysqlUserId());
                    if (userOpt.isPresent()) {
                        String name = userOpt.get().getName();
                        if (name != null && !name.isBlank()) {
                            profile.setOfficerName(name.trim());
                        } else {
                            profile.setOfficerName(profile.getDesignation());
                        }
                    } else {
                        profile.setOfficerName(profile.getDesignation());
                    }
                    officerRepository.save(profile);
                    updatedCount++;
                }
            }
            if (updatedCount > 0) {
                log.info("Synchronized officer names for {} profiles from MySQL users store.", updatedCount);
            }
        } catch (Exception e) {
            log.warn("Non-fatal: could not synchronize existing officer names on startup: {}", e.getMessage());
        }
    }
}
