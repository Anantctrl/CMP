package com.copmap.auth.services.impl;

import com.copmap.auth.payload.UserDto;
import com.copmap.auth.services.AuthService;
import com.copmap.auth.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDto registerUser(UserDto userDto) {
        if (userDto.getEmail() == null || userDto.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required for registration");
        }
        if (userDto.getPassword() == null || userDto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for registration");
        }

        userDto.setPassword(
                passwordEncoder.encode(userDto.getPassword())
        );

        // Honor supervisor flag from registration checkbox
        boolean isSupervisor = Boolean.TRUE.equals(userDto.getSupervisor());
        userDto.setSupervisor(isSupervisor);
        if (!isSupervisor) {
            userDto.setRole(null);
        }

        return userService.createUser(userDto);
    }
}
