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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@DisplayName("Distributed Caching & Redis Fault Tolerance Test Suite")
public class DistributedCachingAndRedisFaultToleranceTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private OfficerDirectoryService officerDirectoryService;

    @Nested
    @DisplayName("Redis Serializer & Infrastructure Validation")
    class SerializerTests {

        @Test
        @DisplayName("Verify Key and Value Serializers in RedisTemplate")
        void shouldVerifySerializers() {
            assertThat(redisTemplate.getKeySerializer())
                    .as("Key serializer must be StringRedisSerializer")
                    .isInstanceOf(StringRedisSerializer.class);

            assertThat(redisTemplate.getValueSerializer())
                    .as("Value serializer must be GenericJackson2JsonRedisSerializer")
                    .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        }

        @Test
        @DisplayName("Verify Polymorphic OffsetDateTime Serialization for Live Location")
        void shouldSerializeAndDeserializeLiveLocationWithTimestamps() {
            UUID officerId = UUID.randomUUID();
            String redisKey = "test:officer:live:" + officerId;

            OffsetDateTime now = OffsetDateTime.now();
            OfficerLiveLocationResponse location = new OfficerLiveLocationResponse(
                    officerId,
                    "Inspector Test",
                    "SWAT Officer",
                    18.52043,
                    73.85674,
                    5.0,
                    now,
                    now,
                    "FRESH"
            );

            try {
                // Write to Redis with 60-second TTL
                redisTemplate.opsForValue().set(redisKey, location, Duration.ofSeconds(60));

                // Read back
                Object cached = redisTemplate.opsForValue().get(redisKey);
                assertThat(cached)
                        .isNotNull()
                        .isInstanceOf(OfficerLiveLocationResponse.class);

                OfficerLiveLocationResponse readBack = (OfficerLiveLocationResponse) cached;
                assertThat(readBack.officerId()).isEqualTo(officerId);
                assertThat(readBack.officerName()).isEqualTo("Inspector Test");
                assertThat(readBack.freshnessStatus()).isEqualTo("FRESH");
                assertThat(readBack.latitude()).isEqualTo(18.52043);
            } finally {
                redisTemplate.delete(redisKey);
            }
        }
    }

    @Nested
    @DisplayName("Cache-Aside, Invalidation & Stale Cache Simulation")
    class CacheLifecycleTests {

        @Test
        @DisplayName("Simulate Cache Miss, Population, and Explicit Eviction")
        void shouldPopulateAndEvictOfficerDirectoryCache() {
            Cache cache = cacheManager.getCache("officers_directory");
            assertThat(cache).isNotNull();

            // 1. Evict any existing cache
            officerDirectoryService.evictDirectoryCache();
            assertThat(cache.get("all")).isNull();

            // 2. Query directory (triggers DB load and cache population)
            List<OfficerDirectoryDto> firstFetch = officerDirectoryService.getAllOfficersCached();
            assertThat(firstFetch).isNotNull();

            // 3. Verify Redis now holds the key
            Cache.ValueWrapper wrapper = cache.get("all");
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isInstanceOf(List.class);

            // 4. Second query should be served from cache
            List<OfficerDirectoryDto> secondFetch = officerDirectoryService.getAllOfficersCached();
            assertThat(secondFetch).isNotNull();
            assertThat(secondFetch.size()).isEqualTo(firstFetch.size());

            // 5. Explicit Eviction
            officerDirectoryService.evictDirectoryCache();
            assertThat(cache.get("all")).isNull();
        }

        @Test
        @DisplayName("Simulate Stale Cache Scenario and Manual Flush Resolution")
        void shouldHandleStaleCacheAndManualFlush() {
            // Populate cache
            officerDirectoryService.getAllOfficersCached();

            Cache cache = cacheManager.getCache("officers_directory");
            assertThat(cache.get("all")).isNotNull();

            // When an out-of-band DB update occurs, cache is stale until eviction
            // Test that manual eviction endpoint clears Redis completely:
            officerDirectoryService.evictDirectoryCache();
            assertThat(cache.get("all")).isNull();

            // Next read re-synchronizes with PostgreSQL
            List<OfficerDirectoryDto> refreshed = officerDirectoryService.getAllOfficersCached();
            assertThat(refreshed).isNotNull();
            assertThat(cache.get("all")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Chaos & Concurrency Scenarios")
    class ChaosAndConcurrencyTests {

        @Test
        @DisplayName("Simulate Cache Stampede (Dog-piling with 20 Concurrent Threads)")
        void shouldHandleCacheStampedeWithoutDeadlockOrErrors() throws Exception {
            // Ensure any unsynced test users have profiles provisioned sequentially first
            officerDirectoryService.getAllOfficersCached();

            // Evict cache to create cold stampede condition
            officerDirectoryService.evictDirectoryCache();

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<List<OfficerDirectoryDto>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    return officerDirectoryService.getAllOfficersCached();
                }));
            }

            // Wait for all threads to align, then trigger stampede
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<List<OfficerDirectoryDto>> future : futures) {
                List<OfficerDirectoryDto> result = future.get(30, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }

            executor.shutdownNow();

            // Verify cache is populated after stampede
            Cache cache = cacheManager.getCache("officers_directory");
            assertThat(cache.get("all")).isNotNull();
        }

        @Test
        @DisplayName("Simulate Corrupted Cache Key Handling (Graceful Deserialization Recovery)")
        void shouldTolerateCorruptedCacheValueWithoutApplicationCrash() {
            String corruptedKey = "officer:live:" + UUID.randomUUID();

            try {
                // Write an incompatible raw payload that cannot deserialize to OfficerLiveLocationResponse
                redisTemplate.opsForValue().set(corruptedKey, "MALFORMED_NON_OBJECT_PAYLOAD", Duration.ofSeconds(30));

                // Reading should not crash the JVM; standard error handling handles the class mismatch
                Object cached = redisTemplate.opsForValue().get(corruptedKey);
                assertThat(cached).isNotNull();
            } finally {
                redisTemplate.delete(corruptedKey);
            }
        }
    }
}
