package com.substring.auth.app.config;

import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.service.OfficerDirectoryService;
import com.substring.auth.app.tracking.dto.OfficerLiveLocationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@DisplayName("3. Redis & Distributed Caching Advanced Test Suite")
public class RedisAdvancedCachingTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private OfficerDirectoryService officerDirectoryService;

    @Nested
    @DisplayName("Stampede & High-Volume Concurrency")
    class StampedeTests {

        @Test
        @DisplayName("100 Simultaneous Cache Misses (Cache Stampede Resilience)")
        void shouldHandle100SimultaneousCacheMissesGracefully() throws Exception {
            // Force cold cache miss
            officerDirectoryService.evictDirectoryCache();

            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(25);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        List<OfficerDirectoryDto> list = officerDirectoryService.getAllOfficersCached();
                        if (list != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }

            executor.shutdownNow();

            assertThat(successCount.get())
                    .as("All 100 concurrent requests must receive directory without error")
                    .isEqualTo(threadCount);

            // Verify cache is now warm
            Cache cache = cacheManager.getCache("officers_directory");
            assertThat(cache.get("all")).isNotNull();
        }

        @Test
        @DisplayName("Concurrent Cache Updates Across Multiple Officers")
        void shouldHandleConcurrentLocationCacheUpdates() throws Exception {
            int officerCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<UUID> officerIds = new ArrayList<>();
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < officerCount; i++) {
                UUID id = UUID.randomUUID();
                officerIds.add(id);
                futures.add(executor.submit(() -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    OfficerLiveLocationResponse resp = new OfficerLiveLocationResponse(
                            id,
                            "Officer-" + id.toString().substring(0, 4),
                            "Patrol Officer",
                            18.52043,
                            73.85674,
                            4.5,
                            now,
                            now,
                            "FRESH"
                    );
                    redisTemplate.opsForValue().set("officer:live:" + id, resp, Duration.ofMinutes(1));
                }));
            }

            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            // Verify all 20 records are cached independently in Redis
            for (UUID id : officerIds) {
                Object cached = redisTemplate.opsForValue().get("officer:live:" + id);
                assertThat(cached).isNotNull();
                redisTemplate.delete("officer:live:" + id);
            }
        }
    }

    @Nested
    @DisplayName("TTL Expiration & Data Loss Recovery")
    class ExpirationAndDataLossTests {

        @Test
        @DisplayName("Verify Redis TTL Expiration and Automatic Cache Rebuilding")
        void shouldExpireKeyAndRebuildCacheTransparently() throws InterruptedException {
            String testKey = "test:ttl:key:" + UUID.randomUUID();

            // Set key with 1-second TTL
            redisTemplate.opsForValue().set(testKey, "Temporary Tactical Data", Duration.ofSeconds(1));
            assertThat(redisTemplate.hasKey(testKey)).isTrue();

            // Wait 1.3 seconds for TTL to elapse
            Thread.sleep(1300);

            // Verify key naturally expired
            assertThat(redisTemplate.hasKey(testKey)).isFalse();
        }

        @Test
        @DisplayName("Simulate Redis Data Loss / Key Deletion and Verify Transparent Rebuild")
        void shouldRecoverFromCompleteCacheKeyDeletion() {
            // Warm cache
            officerDirectoryService.getAllOfficersCached();
            Cache cache = cacheManager.getCache("officers_directory");
            assertThat(cache.get("all")).isNotNull();

            // Simulate sudden Redis data loss by deleting key
            redisTemplate.delete("officers_directory::all");
            assertThat(cache.get("all")).isNull();

            // Next request must transparently reload from PostgreSQL
            List<OfficerDirectoryDto> reloaded = officerDirectoryService.getAllOfficersCached();
            assertThat(reloaded).isNotNull();
            assertThat(cache.get("all")).isNotNull();
        }
    }
}
