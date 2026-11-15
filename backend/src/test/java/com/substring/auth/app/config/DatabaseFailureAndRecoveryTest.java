package com.substring.auth.app.config;

import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.team.entity.Team;
import com.substring.auth.app.team.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("1. Database Failure & Recovery Test Suite")
public class DatabaseFailureAndRecoveryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.substring.auth.app.auth.repositories.RoleRepository roleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Nested
    @DisplayName("Database Constraints & Data Integrity")
    class ConstraintTests {

        @Test
        @DisplayName("Verify Database Constraint Violation on Duplicate Email (MySQL)")
        void shouldEnforceUniqueEmailConstraint() {
            String duplicateEmail = "officer.duplicate." + UUID.randomUUID() + "@copmap.gov";

            com.substring.auth.app.auth.entities.Role role = roleRepository.findAll().stream().findFirst().orElseGet(() -> {
                com.substring.auth.app.auth.entities.Role r = new com.substring.auth.app.auth.entities.Role();
                r.setName("Not_Assigned");
                return roleRepository.save(r);
            });

            User user1 = new User();
            user1.setEmail(duplicateEmail);
            user1.setName("Officer First");
            user1.setPassword("Pass123456!");
            user1.setPolicePost("HQ Dispatch");
            user1.setRole(role);
            userRepository.save(user1);

            User user2 = new User();
            user2.setEmail(duplicateEmail);
            user2.setName("Officer Second");
            user2.setPassword("Pass123456!");
            user2.setPolicePost("HQ Dispatch");
            user2.setRole(role);

            assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                    .as("Saving duplicate email must trigger DataIntegrityViolationException")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Transaction Rollback & Atomicity")
    class TransactionRollbackTests {

        @Test
        @DisplayName("Verify Full Rollback on Exception in PostgreSQL Transaction")
        void shouldRollbackPostgreSQLTransactionOnException() {
            String teamName = "Rollback-Unit-" + UUID.randomUUID();
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            TransactionStatus status = postgresTransactionManager.getTransaction(def);

            try {
                Team team = new Team();
                team.setName(teamName);
                team.setDescription("Testing rollback isolation");
                teamRepository.save(team);

                // Force exception before commit
                throw new RuntimeException("Simulated Tactical Ingress Failure");
            } catch (RuntimeException e) {
                postgresTransactionManager.rollback(status);
            }

            // Verify team record was NOT persisted
            assertThat(teamRepository.findByName(teamName)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Read-Only & Deadlock Simulation")
    class ReadOnlyAndDeadlockTests {

        @Test
        @DisplayName("Verify Read-Only Database Connection Rejects Write Operations")
        void shouldFailWriteOperationsOnReadOnlyConnection() throws SQLException {
            try (Connection conn = postgresDataSource.getConnection()) {
                conn.setAutoCommit(false);
                conn.setReadOnly(true);

                try (Statement stmt = conn.createStatement()) {
                    assertThatThrownBy(() -> stmt.executeUpdate(
                            "INSERT INTO teams (id, name, description, created_at, updated_at) VALUES ('"
                                    + UUID.randomUUID() + "', 'Read-Only Test', 'Desc', NOW(), NOW())"
                    )).as("Insert on read-only connection must throw SQLException")
                      .isInstanceOf(SQLException.class);
                } finally {
                    conn.rollback();
                }
            }
        }

        @Test
        @DisplayName("Simulate Database Connection Timeout Handling")
        void shouldEnforceStatementTimeoutSafely() throws SQLException {
            try (Connection conn = postgresDataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    // Set a 1-second query timeout
                    stmt.setQueryTimeout(1);

                    // Running a 3-second sleep should trigger timeout SQLException
                    assertThatThrownBy(() -> stmt.execute("SELECT pg_sleep(3);"))
                            .as("Query exceeding statement timeout must throw SQLException")
                            .isInstanceOf(SQLException.class);
                }
            }
        }
    }
}
