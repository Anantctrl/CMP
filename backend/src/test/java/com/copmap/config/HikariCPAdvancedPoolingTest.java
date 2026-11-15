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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@DisplayName("2. HikariCP Connection Pooling Advanced Test Suite")
public class HikariCPAdvancedPoolingTest {

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Nested
    @DisplayName("High-Concurrency Connection Acquisition")
    class ConcurrencyTests {

        @Test
        @DisplayName("100 Concurrent Threads Requesting Connections from Connection Pool")
        void shouldHandle100ConcurrentConnectionRequests() throws Exception {
            int taskCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(25);
            CountDownLatch readyLatch = new CountDownLatch(taskCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        try (Connection conn = postgresDataSource.getConnection();
                             Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT 1")) {
                            if (rs.next() && rs.getInt(1) == 1) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Handled
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            executor.shutdownNow();

            assertThat(successCount.get())
                    .as("All 100 concurrent requests must complete successfully")
                    .isEqualTo(taskCount);
        }
    }

    @Nested
    @DisplayName("Connection Invalidation & Leak Detection")
    class InvalidationAndLeakTests {

        @Test
        @DisplayName("Verify Database Connection Invalidation & Automatic Eviction")
        void shouldEvictInvalidConnectionAndProvisionHealthyOne() throws SQLException {
            HikariDataSource pgHikari = (HikariDataSource) postgresDataSource;

            Connection conn = pgHikari.getConnection();
            assertThat(conn.isValid(2)).isTrue();

            // Evict connection from Hikari pool and close wrapper
            pgHikari.evictConnection(conn);
            conn.close();
            assertThat(conn.isClosed()).isTrue();

            // Next getConnection() call on pool must provision a fresh, healthy connection
            try (Connection freshConn = pgHikari.getConnection()) {
                assertThat(freshConn).isNotNull();
                assertThat(freshConn.isValid(2)).isTrue();
                assertThat(freshConn).isNotSameAs(conn);
            }
        }

        @Test
        @DisplayName("Verify HikariCP Leak Detection Threshold Configuration")
        void shouldSupportLeakDetectionConfiguration() {
            HikariDataSource pgHikari = (HikariDataSource) postgresDataSource;

            HikariConfig leakConfig = new HikariConfig();
            leakConfig.setJdbcUrl(pgHikari.getJdbcUrl());
            leakConfig.setUsername(pgHikari.getUsername());
            leakConfig.setPassword(pgHikari.getPassword());
            leakConfig.setMaximumPoolSize(2);
            leakConfig.setLeakDetectionThreshold(2000); // 2000ms leak alert threshold
            leakConfig.setPoolName("Test-Leak-Pool");

            try (HikariDataSource leakPool = new HikariDataSource(leakConfig)) {
                assertThat(leakPool.getLeakDetectionThreshold()).isEqualTo(2000);

                // Acquire connection and release safely within threshold
                assertThatNoException().isThrownBy(() -> {
                    try (Connection conn = leakPool.getConnection()) {
                        assertThat(conn.isValid(1)).isTrue();
                    }
                });
            }
        }
    }
}
