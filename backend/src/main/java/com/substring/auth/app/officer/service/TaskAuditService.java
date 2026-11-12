package com.substring.auth.app.officer.service;

import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.entity.TaskAssignment;
import com.substring.auth.app.officer.entity.TaskAuditLog;
import com.substring.auth.app.officer.repository.TaskAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TaskAuditService {

    private final TaskAuditLogRepository auditRepository;

    public TaskAuditService(TaskAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(transactionManager = "postgresTransactionManager")
    public void record(
            Task task,
            TaskAssignment assignment,
            OfficerProfile changedBy,
            String action,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            String remarks
    ) {
        TaskAuditLog log = new TaskAuditLog();

        log.setTask(task);
        log.setAssignment(assignment);
        log.setChangedBy(changedBy);
        log.setAction(action);
        log.setOldValues(oldValues);
        log.setNewValues(newValues);
        log.setRemarks(remarks);

        auditRepository.save(log);
    }
}
