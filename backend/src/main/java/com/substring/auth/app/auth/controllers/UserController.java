package com.substring.auth.app.auth.controllers;

import com.substring.auth.app.auth.payload.UserDto;
import com.substring.auth.app.auth.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    // Create user API
    @PostMapping
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(userDto));
    }

    // Get all users API
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<Iterable<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // Get user by email
    @GetMapping("/email/{email}")
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    // Delete user
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") UUID userId) {
        userService.deleteUser(userId.toString());
        return ResponseEntity.noContent().build();
    }

    // Get user by id
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('Supervisor', 'Administrator')")
    public ResponseEntity<UserDto> getUserById(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId.toString()));
    }
}
