package com.copmap.auth.services.impl;

import com.copmap.auth.config.AppConstants;
import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.helpers.UserHelper;
import com.copmap.auth.payload.UserDto;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.auth.services.UserService;
import com.copmap.exceptions.ResourceNotFoundException;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.repository.OfficerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

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

        if (user.getPolicePost() == null || user.getPolicePost().isBlank()) {
            user.setPolicePost("General Duty");
        }

        boolean isSupervisorRequested = Boolean.TRUE.equals(userDto.getSupervisor())
                || (userDto.getRole() != null && "Supervisor".equalsIgnoreCase(userDto.getRole().getName()));

        if (isSupervisorRequested) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAuthorizedCaller = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                    .anyMatch(a -> "Supervisor".equalsIgnoreCase(a.getAuthority())
                            || "ROLE_Supervisor".equalsIgnoreCase(a.getAuthority())
                            || "Administrator".equalsIgnoreCase(a.getAuthority())
                            || "ROLE_Administrator".equalsIgnoreCase(a.getAuthority()));

            boolean isAnonymous = auth == null || !auth.isAuthenticated()
                    || auth instanceof AnonymousAuthenticationToken
                    || "anonymousUser".equals(auth.getPrincipal());

            if (isAuthorizedCaller || isAnonymous) {
                Role supervisorRole = roleRepository.findByName("Supervisor")
                        .orElseGet(() -> {
                            Role r = new Role();
                            r.setName("Supervisor");
                            return roleRepository.save(r);
                        });
                user.setRole(supervisorRole);
            } else {
                log.warn("[RBAC VIOLATION] Attempt by non-supervisor caller to provision Supervisor account for email: {}", userDto.getEmail());
                throw new AccessDeniedException("Unauthorized: Only existing Supervisors or Administrators can grant Supervisor privileges");
            }
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
        } catch (Exception e) {
            log.warn("[CROSS-DB SYNC] Failed to provision/sync OfficerProfile in PostgreSQL for user ID {}: {}",
                    savedUser.getId(), e.getMessage());
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
