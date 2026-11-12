package com.substring.auth.app.officer.repository;

import com.substring.auth.app.officer.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(String status);

    List<Task> findByActionType(String actionType);

    List<Task> findByCreatedById(UUID officerId);
}
