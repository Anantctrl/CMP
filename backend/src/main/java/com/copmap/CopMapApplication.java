package com.copmap;

import com.copmap.auth.config.AppConstants;
import com.copmap.auth.entities.Role;
import com.copmap.auth.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.UUID;

@SpringBootApplication
@RequiredArgsConstructor
public class CopMapApplication implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public static void main(String[] args) {
        SpringApplication.run(CopMapApplication.class, args);
    }

    @Override
    public void run(String... args) {
        roleRepository.findByName(AppConstants.DEFAULT_ROLE)
                .ifPresentOrElse(
                        role -> System.out.println(
                                "Default role already exists: " + role.getName()
                        ),
                        () -> {
                            Role role = new Role();
                            role.setId(UUID.randomUUID());
                            role.setName(AppConstants.DEFAULT_ROLE);
                            roleRepository.save(role);
                            System.out.println(
                                    "Created default role: " + AppConstants.DEFAULT_ROLE
                            );
                        }
                );

        roleRepository.findByName("Supervisor")
                .ifPresentOrElse(
                        role -> System.out.println(
                                "Supervisor role already exists: " + role.getName()
                        ),
                        () -> {
                            Role role = new Role();
                            role.setId(UUID.randomUUID());
                            role.setName("Supervisor");
                            roleRepository.save(role);
                            System.out.println("Created Supervisor role");
                        }
                );
    }
}
