package com.substring.auth.app.officer.service;

import com.substring.auth.app.officer.dto.CreateTaskRequest;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.repository.TaskRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TaskAuditService auditService;

    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "PATROLLING",
            "NAKABANDI",
            "BANDOBAST"
    );

    public TaskService(
            TaskRepository taskRepository,
            CurrentOfficerService currentOfficerService,
            TaskAuditService auditService
    ) {
        this.taskRepository = taskRepository;
        this.currentOfficerService = currentOfficerService;
        this.auditService = auditService;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public Task createTask(CreateTaskRequest request) {

        verifySupervisorOrAdmin();

        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }

        String actionType = request.actionType()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!ALLOWED_ACTION_TYPES.contains(actionType)) {
            throw new IllegalArgumentException("Invalid action type");
        }

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException(
                    "Latitude and longitude must be provided together"
            );
        }

        if (request.latitude() != null &&
                (request.latitude() < -90 || request.latitude() > 90)) {
            throw new IllegalArgumentException("Invalid latitude");
        }

        if (request.longitude() != null &&
                (request.longitude() < -180 || request.longitude() > 180)) {
            throw new IllegalArgumentException("Invalid longitude");
        }

        OfficerProfile creator = currentOfficerService.getCurrentOfficer();

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setActionType(actionType);
        task.setDescription(request.description());
        task.setLocationName(request.locationName());
        task.setLatitude(request.latitude());
        task.setLongitude(request.longitude());
        task.setCreatedBy(creator);
        task.setStatus("DRAFT");

        Task savedTask = taskRepository.save(task);

        auditService.record(
                savedTask,
                null,
                creator,
                "TASK_CREATED",
                null,
                Map.of(
                        "title", savedTask.getTitle(),
                        "actionType", savedTask.getActionType(),
                        "status", savedTask.getStatus()
                ),
                "Task created"
        );

        return savedTask;
    }

    private void verifySupervisorOrAdmin() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        boolean authorized = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("Supervisor")
                        || authority.getAuthority().equals("Administrator")
                );

        if (!authorized) {
            throw new AccessDeniedException(
                    "Only supervisors or administrators can create tasks"
            );
        }
    }
}
