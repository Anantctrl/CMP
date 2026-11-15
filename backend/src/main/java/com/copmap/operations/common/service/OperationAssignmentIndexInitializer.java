package com.copmap.operations.common.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Component
public class OperationAssignmentIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate postgresJdbcTemplate;

    public OperationAssignmentIndexInitializer(
            @Qualifier("postgresDataSource") DataSource postgresDataSource
    ) {
        this.postgresJdbcTemplate = new JdbcTemplate(postgresDataSource);
    }

    @Override
    @Transactional(transactionManager = "postgresTransactionManager")
    public void run(ApplicationArguments args) {
        postgresJdbcTemplate.execute(
                "ALTER TABLE operation_assignments "
                        + "DROP CONSTRAINT IF EXISTS uq_operation_location_officer"
        );
        postgresJdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS "
                        + "uq_operation_location_active_officer "
                        + "ON operation_assignments (location_id, officer_id) "
                        + "WHERE status IN "
                        + "('ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS')"
        );
    }
}
