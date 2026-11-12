package com.substring.auth.app.officer.controller;

import com.substring.auth.app.officer.dto.CreateTaskRequest;
import com.substring.auth.app.officer.dto.TaskResponse;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.service.TaskService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request
    ) {
        Task task = taskService.createTask(request);

        TaskResponse response = new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getActionType(),
                task.getDescription(),
                task.getLocationName(),
                task.getLatitude(),
                task.getLongitude(),
                task.getCreatedBy().getId(),
                task.getStatus()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
