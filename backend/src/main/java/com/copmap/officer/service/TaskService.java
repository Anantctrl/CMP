package com.copmap.officer.service;

import com.copmap.officer.dto.CreateTaskRequest;
import com.copmap.officer.dto.TaskResponse;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.entity.Task;
import com.copmap.officer.entity.TaskAssignment;
import com.copmap.officer.repository.TaskAssignmentRepository;
import com.copmap.officer.repository.TaskRepository;
import com.copmap.team.entity.Team;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamRepository;
import com.copmap.team.service.TeamAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final CurrentOfficerService currentOfficerService;
    private final TaskAuditService auditService;
    private final TeamRepository teamRepository;
    private final TeamAuthorizationService authService;

    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "PATROLLING",
            "NAKABANDI",
            "BANDOBAST",
            "INVESTIGATION",
            "GENERAL_DUTY"
    );

    public TaskService(
            TaskRepository taskRepository,
            TaskAssignmentRepository assignmentRepository,
            CurrentOfficerService currentOfficerService,
            TaskAuditService auditService,
            TeamRepository teamRepository,
            TeamAuthorizationService authService
    ) {
        this.taskRepository = taskRepository;
        this.assignmentRepository = assignmentRepository;
        this.currentOfficerService = currentOfficerService;
        this.auditService = auditService;
        this.teamRepository = teamRepository;
        this.authService = authService;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public Task createTask(CreateTaskRequest request) {

        Team team = null;
        if (request.teamId() != null) {
            team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new EntityNotFoundException("Team not found"));

            authService.requireTeamRole(
                    team.getId(),
                    "Only Supervisors or Senior Planners in this team can create tasks",
                    TeamRole.SENIOR_PLANNER
            );
        } else {
            authService.requireSupervisor("Only supervisors can create global team-unassigned tasks");
        }

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
        task.setTeam(team);
        task.setTitle(request.title().trim());
        task.setActionType(actionType);
        task.setDescription(request.description());
        task.setLocationName(request.locationName());
        task.setLatitude(request.latitude());
        task.setLongitude(request.longitude());
        task.setCreatedBy(creator);
        task.setStatus("DRAFT");

        Task savedTask = taskRepository.save(task);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("id", savedTask.getId());
        newValues.put("title", savedTask.getTitle());
        newValues.put("actionType", savedTask.getActionType());
        newValues.put("status", savedTask.getStatus());
        newValues.put("teamId", team != null ? team.getId() : null);

        auditService.record(
                savedTask,
                null,
                creator,
                "TASK_CREATED",
                null,
                newValues,
                "Task created"
        );

        return savedTask;
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<TaskResponse> getTaskResponsesForTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new EntityNotFoundException("Team not found");
        }

        authService.requireTeamRole(
                teamId,
                "You are not authorized to view tasks for this team",
                TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.CONTROL_OPERATOR, TeamRole.ADMINISTRATOR, TeamRole.FIELD_OPERATOR
        );

        List<Task> tasks = taskRepository.findAllByTeam_IdWithDetails(teamId);
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<TaskResponse> getAllAuthorizedTaskResponses() {
        List<Task> tasks;
        if (authService.isSupervisor()) {
            tasks = taskRepository.findAllWithDetails();
        } else {
            List<UUID> authorizedTeamIds = authService.getAuthorizedTeamIds();
            if (authorizedTeamIds.isEmpty()) {
                return List.of();
            }
            tasks = taskRepository.findAllByTeam_IdInWithDetails(authorizedTeamIds);
        }

        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public TaskResponse getTaskResponse(UUID taskId) {
        Task task = getTask(taskId);
        return toResponse(task);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public TaskResponse toResponse(Task task) {
        List<TaskAssignment> assignments = assignmentRepository.findByTaskIdWithOfficer(task.getId());
        List<String> assignedOfficerNames = assignments.stream()
                .map(a -> {
                    String name = null;
                    if (a.getOfficer() != null) {
                        name = a.getOfficer().getOfficerName();
                        if (name == null || name.isBlank()) {
                            name = a.getOfficer().getDesignation();
                        }
                    }
                    return name;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        UUID teamId = task.getTeam() != null ? task.getTeam().getId() : null;
        UUID createdById = task.getCreatedBy() != null ? task.getCreatedBy().getId() : null;

        return new TaskResponse(
                task.getId(),
                teamId,
                task.getTitle(),
                task.getActionType(),
                task.getDescription(),
                task.getLocationName(),
                task.getLatitude(),
                task.getLongitude(),
                createdById,
                task.getStatus(),
                assignedOfficerNames
        );
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Task> getTasksForTeam(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new EntityNotFoundException("Team not found");
        }

        authService.requireTeamRole(
                teamId,
                "You are not authorized to view tasks for this team",
                TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.CONTROL_OPERATOR, TeamRole.ADMINISTRATOR, TeamRole.FIELD_OPERATOR
        );

        return taskRepository.findAllByTeam_IdWithDetails(teamId);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public List<Task> getAllAuthorizedTasks() {
        if (authService.isSupervisor()) {
            return taskRepository.findAllWithDetails();
        }

        List<UUID> authorizedTeamIds = authService.getAuthorizedTeamIds();
        if (authorizedTeamIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findAllByTeam_IdInWithDetails(authorizedTeamIds);
    }

    @Transactional(
            readOnly = true,
            transactionManager = "postgresTransactionManager"
    )
    public Task getTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        if (task.getTeam() != null) {
            authService.requireTeamRole(
                    task.getTeam().getId(),
                    "You are not authorized to view this task",
                    TeamRole.SENIOR_PLANNER, TeamRole.TEAM_LEAD, TeamRole.CONTROL_OPERATOR, TeamRole.ADMINISTRATOR, TeamRole.FIELD_OPERATOR
            );
        } else {
            authService.requireSupervisor("Only supervisors can view unassigned tasks");
        }

        return task;
    }
}
