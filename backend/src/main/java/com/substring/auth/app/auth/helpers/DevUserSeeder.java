package com.substring.auth.app.auth.helpers;

import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DevUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void seedOrUpdateDevUsers() {
        try {
            String seedPassword = System.getenv("DEV_SEED_PASSWORD");
            if (seedPassword == null || seedPassword.isBlank()) {
                log.warn("DevUserSeeder skipped: DEV_SEED_PASSWORD environment variable is not set");
                return;
            }
            String defaultPassword = passwordEncoder.encode(seedPassword);

            // 1. Specific officers mapped to Team Alpha
            updateOrCreateOfficer(
                    UUID.fromString("dbad46f0-e7fe-46f9-ae9c-23c1286ce3bd"),
                    "planner@police.gov",
                    "Test Senior Inspector",
                    "Senior Inspector",
                    defaultPassword
            );

            updateOrCreateOfficer(
                    UUID.fromString("72785dc4-5ed2-4da9-b9ed-90811333f4e5"),
                    "lead@police.gov",
                    "Test Inspector",
                    "Inspector",
                    defaultPassword
            );

            updateOrCreateOfficer(
                    UUID.fromString("de546526-c77e-4b89-b463-36bb888212ed"),
                    "control@police.gov",
                    "Test Sub Inspector",
                    "Sub Inspector",
                    defaultPassword
            );

            // 2. Ensure supervisor account is accessible
            Role supervisorRole = roleRepository.findByName("Supervisor")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName("Supervisor");
                        return roleRepository.save(r);
                    });

            Optional<User> supOpt = userRepository.findByEmail("supervisor@police.gov");
            if (supOpt.isPresent()) {
                User sup = supOpt.get();
                sup.setPassword(defaultPassword);
                sup.setRole(supervisorRole);
                sup.setEnable(true);
                userRepository.save(sup);
            } else {
                User sup = new User();
                sup.setEmail("supervisor@police.gov");
                sup.setName("Divisional Supervisor");
                sup.setPolicePost("DGP");
                sup.setPassword(defaultPassword);
                sup.setRole(supervisorRole);
                sup.setEnable(true);
                userRepository.save(sup);
            }

            // 3. For any existing test users in MySQL, ensure their password matches the configured seed password
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                if ("hashedpassword".equals(u.getPassword())) {
                    u.setPassword(defaultPassword);
                    u.setEnable(true);
                    userRepository.save(u);
                }
            }

            log.info("DevUserSeeder initialized successfully using the configured DEV_SEED_PASSWORD.");
        } catch (Exception e) {
            log.warn("DevUserSeeder warning: {}", e.getMessage());
        }
    }

    private void updateOrCreateOfficer(UUID userId, String targetEmail, String name, String post, String encodedPassword) {
        Role officerRole = roleRepository.findByName("Not_Assigned")
                .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Not_Assigned");
                    return roleRepository.save(r);
                });

        Optional<User> byId = userRepository.findById(userId);
        if (byId.isPresent()) {
            User u = byId.get();
            // If another user already has targetEmail, clean it up or keep
            Optional<User> existingEmailUser = userRepository.findByEmail(targetEmail);
            if (existingEmailUser.isEmpty() || existingEmailUser.get().getId().equals(userId)) {
                u.setEmail(targetEmail);
            }
            u.setName(name);
            u.setPolicePost(post);
            u.setPassword(encodedPassword);
            u.setEnable(true);
            userRepository.save(u);
        } else {
            Optional<User> byEmail = userRepository.findByEmail(targetEmail);
            if (byEmail.isPresent()) {
                User u = byEmail.get();
                u.setName(name);
                u.setPolicePost(post);
                u.setPassword(encodedPassword);
                u.setEnable(true);
                userRepository.save(u);
            } else {
                User u = new User();
                u.setId(userId);
                u.setEmail(targetEmail);
                u.setName(name);
                u.setPolicePost(post);
                u.setPassword(encodedPassword);
                u.setRole(officerRole);
                u.setEnable(true);
                userRepository.save(u);
            }
        }
    }
}
