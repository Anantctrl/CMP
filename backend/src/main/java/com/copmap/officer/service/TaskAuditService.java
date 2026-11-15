package com.copmap.officer.service;

import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.entity.Task;
import com.copmap.officer.entity.TaskAssignment;
import com.copmap.officer.entity.TaskAuditLog;
import com.copmap.officer.repository.TaskAuditLogRepository;
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
