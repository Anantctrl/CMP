package com.copmap.officer.repository;

import com.copmap.officer.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(String status);

    List<Task> findByActionType(String actionType);

    List<Task> findByCreatedById(UUID officerId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN FETCH t.team LEFT JOIN FETCH t.createdBy WHERE t.team.id = :teamId")
    List<Task> findAllByTeam_IdWithDetails(@Param("teamId") UUID teamId);

    List<Task> findAllByTeam_Id(UUID teamId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN FETCH t.team LEFT JOIN FETCH t.createdBy WHERE t.team.id IN :teamIds")
    List<Task> findAllByTeam_IdInWithDetails(@Param("teamIds") List<UUID> teamIds);

    List<Task> findAllByTeam_IdIn(List<UUID> teamIds);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN FETCH t.team LEFT JOIN FETCH t.createdBy")
    List<Task> findAllWithDetails();

    long countByTeam_Id(UUID teamId);
}
