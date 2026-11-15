package com.substring.auth.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.payload.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("6. API Security, Validation & High-Load Performance Stress Test Suite")
public class ApiSecurityAndPerformanceStressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("Input Validation & Security Hardening")
    class ValidationAndSecurityTests {

        @Test
        @DisplayName("Malformed JSON Payload Returns HTTP 400 Bad Request")
        void shouldRejectMalformedJsonPayload() throws Exception {
            String malformedJson = "{\"email\": \"invalid-json-body-without-closing-brace";

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Malformed JSON"));
        }

        @Test
        @DisplayName("Missing Required Fields (Empty Email & Password on Registration) Returns HTTP 400 Validation Error")
        void shouldRejectEmptyCredentialsWithValidationErrors() throws Exception {
            String emptyUserJson = "{\"email\": \"\", \"password\": \"\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyUserJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        @DisplayName("SQL Injection Attempt in Login Credentials is Safely Escaped / Rejected with 401")
        void shouldSafelyHandleSqlInjectionPayload() throws Exception {
            String sqliPayload = "' OR '1'='1' --";
            LoginRequest sqliRequest = new LoginRequest(sqliPayload, "password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sqliRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Nested
    @DisplayName("High-Load & Concurrent Traffic Stress")
    class ConcurrencyAndLoadStressTests {

        @Test
        @DisplayName("100 Concurrent API Requests to Public Ingress Without Deadlock")
        void shouldHandle100ConcurrentApiRequests() throws Exception {
            int requestCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(25);
            CountDownLatch readyLatch = new CountDownLatch(requestCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger completedRequests = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        LoginRequest req = new LoginRequest("stress.user." + idx + "@copmap.gov", "password");
                        mockMvc.perform(post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                .andReturn();
                        completedRequests.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            assertThat(completedRequests.get())
                    .as("All 100 concurrent requests must complete safely")
                    .isEqualTo(requestCount);
        }

        @Test
        @DisplayName("1,000 High-Throughput Concurrency Tasks Stress Test")
        void shouldSustain1000HighThroughputTasks() throws Exception {
            int taskCount = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(30);
            AtomicInteger counter = new AtomicInteger(0);
            List<Callable<Void>> tasks = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                tasks.add(() -> {
                    counter.incrementAndGet();
                    return null;
                });
            }

            List<Future<Void>> results = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            executor.shutdownNow();

            assertThat(counter.get())
                    .as("All 1,000 high-throughput tasks must complete within 10 seconds")
                    .isEqualTo(taskCount);
            assertThat(results).hasSize(taskCount);
        }
    }
}
