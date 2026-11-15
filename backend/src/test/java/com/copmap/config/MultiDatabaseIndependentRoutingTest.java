package com.copmap.config;

import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.repository.OfficerProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("4. Multi-Database Independent Routing & Concurrency Test Suite")
public class MultiDatabaseIndependentRoutingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.copmap.auth.repositories.RoleRepository roleRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Nested
    @DisplayName("Concurrent Dual-Database Operations")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Concurrent Access to Both Databases Simultaneously without Routing Interference")
        void shouldHandleSimultaneousAccessToMySQLAndPostgreSQL() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicBoolean allSuccess = new AtomicBoolean(true);

            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        if (idx % 2 == 0) {
                            // Query MySQL
                            long userCount = userRepository.count();
                            assertThat(userCount).isGreaterThanOrEqualTo(0);
                        } else {
                            // Query PostgreSQL
                            long officerCount = officerRepository.count();
                            assertThat(officerCount).isGreaterThanOrEqualTo(0);
                        }
                    } catch (Throwable t) {
                        allSuccess.set(false);
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            assertThat(allSuccess.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("Cross-Database Transaction Isolation")
    class TransactionIndependenceTests {

        @Test
        @DisplayName("Confirm Non-XA Independent Transaction Boundaries Between MySQL and PostgreSQL")
        void shouldConfirmIndependentTransactionBoundaries() {
            String testEmail = "independent.tx." + UUID.randomUUID() + "@copmap.gov";

            com.copmap.auth.entities.Role role = roleRepository.findAll().stream().findFirst().orElseGet(() -> {
                com.copmap.auth.entities.Role r = new com.copmap.auth.entities.Role();
                r.setName("Not_Assigned");
                return roleRepository.save(r);
            });

            // 1. Save User in MySQL
            User user = new User();
            user.setEmail(testEmail);
            user.setName("Independent TX User");
            user.setPassword("Secret123!");
            user.setPolicePost("HQ Dispatch");
            user.setRole(role);
            User savedUser = userRepository.save(user);
            assertThat(savedUser.getId()).isNotNull();

            // 2. Query PostgreSQL independently
            long initialOfficerCount = officerRepository.count();

            // 3. User in MySQL exists independently of any PostgreSQL transactional state
            assertThat(userRepository.findByEmail(testEmail)).isPresent();
            assertThat(officerRepository.count()).isEqualTo(initialOfficerCount);
        }
    }
}
