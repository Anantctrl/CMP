package com.substring.auth.app.officer.controller;

import com.substring.auth.app.officer.dto.CreateTaskRequest;
import com.substring.auth.app.officer.dto.TaskResponse;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.toResponse(task));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(
            @RequestParam(required = false) UUID teamId
    ) {
        if (teamId != null) {
            return ResponseEntity.ok(taskService.getTaskResponsesForTeam(teamId));
        } else {
            return ResponseEntity.ok(taskService.getAllAuthorizedTaskResponses());
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(taskService.getTaskResponse(taskId));
    }
}
