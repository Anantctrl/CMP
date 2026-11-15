package com.copmap.auth.helpers;

import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
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
        // Seed users disabled as per fresh start requirement
        log.info("DevUserSeeder: Seeding is disabled for fresh start.");
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
