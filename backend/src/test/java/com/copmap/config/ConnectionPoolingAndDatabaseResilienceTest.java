package com.copmap.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("Connection Pooling & Database Resilience Test Suite (HikariCP)")
public class ConnectionPoolingAndDatabaseResilienceTest {

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Nested
    @DisplayName("Unit Configuration Checks")
    class PoolConfigurationTests {

        @Test
        @DisplayName("Verify Both MySQL and PostgreSQL Use HikariCP Pools")
        void shouldVerifyHikariCPInstances() {
            assertThat(mysqlDataSource)
                    .as("MySQL DataSource must be an instance of HikariDataSource")
                    .isInstanceOf(HikariDataSource.class);

            assertThat(postgresDataSource)
                    .as("PostgreSQL DataSource must be an instance of HikariDataSource")
                    .isInstanceOf(HikariDataSource.class);

            HikariDataSource mysqlHikari = (HikariDataSource) mysqlDataSource;
            HikariDataSource pgHikari = (HikariDataSource) postgresDataSource;

            assertThat(mysqlHikari.getPoolName()).isNotEmpty();
            assertThat(pgHikari.getPoolName()).isNotEmpty();
            assertThat(mysqlHikari.getJdbcUrl()).contains("mysql");
            assertThat(pgHikari.getJdbcUrl()).contains("postgresql");
        }

        @Test
        @DisplayName("Verify Health Validation Query and Active Connection Retrieval")
        void shouldSuccessfullyAcquireAndReleaseConnections() throws SQLException {
            try (Connection mysqlConn = mysqlDataSource.getConnection();
                 Connection pgConn = postgresDataSource.getConnection()) {

                assertThat(mysqlConn.isValid(5)).isTrue();
                assertThat(pgConn.isValid(5)).isTrue();

                try (Statement stmt = pgConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Nested
    @DisplayName("Pool Overload & Exhaustion Scenarios")
    class PoolExhaustionTests {

        @Test
        @DisplayName("Simulate HikariCP Pool Exhaustion and Verify SQLTransientConnectionException")
        void shouldExhaustPoolAndFailGracefullyOnTimeout() throws Exception {
            HikariDataSource pgHikari = (HikariDataSource) postgresDataSource;

            // Configure an isolated small pool (max 2 connections, 1000ms timeout)
            HikariConfig testConfig = new HikariConfig();
            testConfig.setJdbcUrl(pgHikari.getJdbcUrl());
            testConfig.setUsername(pgHikari.getUsername());
            testConfig.setPassword(pgHikari.getPassword());
            testConfig.setMaximumPoolSize(2);
            testConfig.setConnectionTimeout(1000); // 1-second timeout
            testConfig.setPoolName("Test-Stress-Pool");

            try (HikariDataSource stressPool = new HikariDataSource(testConfig)) {
                CountDownLatch connectionsAcquired = new CountDownLatch(2);
                CountDownLatch releaseLatch = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(3);

                try {
                    // Task 1: Holds connection 1
                    executor.submit(() -> {
                        try (Connection c1 = stressPool.getConnection()) {
                            connectionsAcquired.countDown();
                            releaseLatch.await(5, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                        }
                    });

                    // Task 2: Holds connection 2
                    executor.submit(() -> {
                        try (Connection c2 = stressPool.getConnection()) {
                            connectionsAcquired.countDown();
                            releaseLatch.await(5, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                        }
                    });

                    // Wait until both connections are acquired
                    boolean acquired = connectionsAcquired.await(3, TimeUnit.SECONDS);
                    assertThat(acquired).isTrue();

                    // Now pool is 100% exhausted (2 out of 2 leased)
                    // Attempting to acquire a 3rd connection MUST timeout and throw SQLTransientConnectionException
                    long start = System.currentTimeMillis();
                    assertThatThrownBy(stressPool::getConnection)
                            .as("Acquiring 3rd connection on exhausted pool must fail after timeout")
                            .isInstanceOf(SQLTransientConnectionException.class)
                            .hasMessageContaining("Connection is not available, request timed out");

                    long elapsed = System.currentTimeMillis() - start;
                    assertThat(elapsed).isGreaterThanOrEqualTo(900); // Waited at least ~1000ms

                    // Release connections and verify pool auto-recovery
                    releaseLatch.countDown();

                    // Give a moment for pool to return connection
                    Thread.sleep(200);

                    try (Connection recoveredConn = stressPool.getConnection()) {
                        assertThat(recoveredConn.isValid(2)).isTrue();
                    }
                } finally {
                    releaseLatch.countDown();
                    executor.shutdownNow();
                }
            }
        }

        @Test
        @DisplayName("Simulate Slow Database Query (PostgreSQL pg_sleep)")
        void shouldHandleSlowDatabaseQueryGracefully() throws SQLException {
            try (Connection pgConn = postgresDataSource.getConnection()) {
                long start = System.currentTimeMillis();
                try (Statement stmt = pgConn.createStatement()) {
                    // Execute a controlled 1-second sleep in PostgreSQL
                    stmt.execute("SELECT pg_sleep(1);");
                }
                long duration = System.currentTimeMillis() - start;
                assertThat(duration)
                        .as("Query must wait for PostgreSQL sleep")
                        .isGreaterThanOrEqualTo(950);
            }
        }
    }
}
