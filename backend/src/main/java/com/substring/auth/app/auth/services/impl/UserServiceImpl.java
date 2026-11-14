package com.substring.auth.app.auth.services.impl;

import com.substring.auth.app.auth.config.AppConstants;
import com.substring.auth.app.auth.entities.Provider;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.helpers.UserHelper;
import com.substring.auth.app.auth.payload.UserDto;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.UserService;
import com.substring.auth.app.exceptions.ResourceNotFoundException;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final RoleRepository roleRepository;
    private final OfficerProfileRepository officerRepository;
    private final CacheManager cacheManager;

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public UserDto createUser(UserDto userDto) {
        if (userDto.getEmail() == null || userDto.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new IllegalArgumentException("User with given email already exists");
        }

        User user = modelMapper.map(userDto, User.class);

        user.setProvider(
                userDto.getProvider() != null
                        ? userDto.getProvider()
                        : Provider.LOCAL
        );

        boolean isSupervisorRequested = Boolean.TRUE.equals(userDto.getSupervisor())
                || (userDto.getRole() != null && "Supervisor".equalsIgnoreCase(userDto.getRole().getName()));

        if (isSupervisorRequested) {
            Role supervisorRole = roleRepository.findByName("Supervisor")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName("Supervisor");
                        return roleRepository.save(r);
                    });
            user.setRole(supervisorRole);
        } else {
            Role defaultRole = roleRepository.findByName(AppConstants.DEFAULT_ROLE)
                    .or(() -> roleRepository.findByName("Not Assigned"))
                    .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName(AppConstants.DEFAULT_ROLE);
                        return roleRepository.save(r);
                    });
            user.setRole(defaultRole);
        }

        User savedUser = userRepository.save(user);

        // Auto-provision & sync OfficerProfile in PostgreSQL and evict Redis cache
        try {
            OfficerProfile profile = officerRepository.findByMysqlUserId(savedUser.getId())
                    .orElseGet(() -> {
                        OfficerProfile op = new OfficerProfile();
                        op.setMysqlUserId(savedUser.getId());
                        return op;
                    });
            String desig = (savedUser.getPolicePost() != null && !savedUser.getPolicePost().isBlank())
                    ? savedUser.getPolicePost().trim()
                    : "Officer";
            profile.setDesignation(desig);
            profile.setOfficerName(savedUser.getName() != null && !savedUser.getName().isBlank()
                    ? savedUser.getName()
                    : "Officer");
            officerRepository.save(profile);

            if (cacheManager != null && cacheManager.getCache("officers_directory") != null) {
                cacheManager.getCache("officers_directory").clear();
            }
        } catch (Exception ignored) {
            // Handled gracefully
        }

        return modelMapper.map(savedUser, UserDto.class);
    }

    @Override
    public UserDto getUserByEmail(String email) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given email id "));
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public void deleteUser(String userId) {
        UUID uId = UserHelper.parseUUID(userId);
        User user = userRepository.findById(uId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id"));
        userRepository.delete(user);
    }

    @Override
    public UserDto getUserById(String userId) {
        User user = userRepository.findById(UserHelper.parseUUID(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id"));
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Iterable<UserDto> getAllUsers() {
        return userRepository
                .findAll()
                .stream()
                .map(user -> modelMapper.map(user, UserDto.class))
                .toList();
    }
}
