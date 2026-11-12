package com.substring.auth.app;

import com.substring.auth.app.auth.config.AppConstants;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.UUID;

@SpringBootApplication
@RequiredArgsConstructor
public class AuthBackend implements CommandLineRunner {

    private final RoleRepository roleRepository;

	public static void main(String[] args) {
		SpringApplication.run(AuthBackend.class, args);
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
    }
}
