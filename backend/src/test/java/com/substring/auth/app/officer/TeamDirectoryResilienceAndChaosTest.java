package com.substring.auth.app.officer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.service.OfficerDirectoryService;
import com.substring.auth.app.team.dto.AddTeamMemberRequest;
import com.substring.auth.app.team.dto.CreateTeamRequest;
import com.substring.auth.app.team.dto.TeamResponse;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.repository.TeamMembershipRepository;
import com.substring.auth.app.team.repository.TeamRepository;
import com.substring.auth.app.team.service.TeamService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Team Directory & Officer Directory - Resilience, Chaos & Stress Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TeamDirectoryResilienceAndChaosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfficerDirectoryService directoryService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository membershipRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RedisCacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Role supervisorRole;
    private Role officerRole;

    private User supervisorUser;
    private User plannerUser;
    private User constableUser;

    private OfficerProfile supervisorOfficer;
    private OfficerProfile plannerOfficer;
    private OfficerProfile constableOfficer;

    private String supervisorToken;
    private String plannerToken;
    private String constableToken;

    @BeforeEach
    void setUp() {
        supervisorRole = roleRepository.findByName("Supervisor")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Supervisor");
                    return roleRepository.save(r);
                });

        officerRole = roleRepository.findByName("Not_Assigned")
                .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Not_Assigned");
                    return roleRepository.save(r);
                });

        supervisorUser = createTestUser("sup_dir_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        plannerUser = createTestUser("plan_dir_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        constableUser = createTestUser("const_dir_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

        supervisorOfficer = createTestOfficer(supervisorUser, "Commander");
        plannerOfficer = createTestOfficer(plannerUser, "Senior Planner");
        constableOfficer = createTestOfficer(constableUser, "Constable");

        supervisorToken = jwtService.generateAccessToken(supervisorUser);
        plannerToken = jwtService.generateAccessToken(plannerUser);
        constableToken = jwtService.generateAccessToken(constableUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser(String email, String post, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPolicePost(post);
        u.setName("Officer " + post);
        u.setPassword("$2a$10$abcdefghijklmnopqrstuvxyz123456");
        u.setRole(role);
        u.setEnable(true);
        return userRepository.save(u);
    }

    private OfficerProfile createTestOfficer(User user, String designation) {
        return officerRepository.findByMysqlUserId(user.getId())
                .orElseGet(() -> {
                    OfficerProfile p = new OfficerProfile();
                    p.setMysqlUserId(user.getId());
                    p.setOfficerName(user.getName());
                    p.setDesignation(designation);
                    return officerRepository.save(p);
                });
    }

    private void authenticate(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "Not_Assigned";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority(roleName))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // =========================================================================
    // SECTION 1: CONTROLLER LAYER - HTTP CONTRACT & ROLE-BASED ACCESS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Controller Access: Unauthenticated requests to directory endpoints rejected with 401")
    void shouldRejectUnauthenticatedDirectoryRequests() throws Exception {
        // 1. GET /api/v1/officers without token -> 401
        mockMvc.perform(get("/api/v1/officers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 2. GET /api/v1/officers/available without token -> 401
        mockMvc.perform(get("/api/v1/officers/available"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 3. POST /api/v1/officers/cache/evict without token -> 401
        mockMvc.perform(post("/api/v1/officers/cache/evict"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Controller RBAC: Constable rejected (403) while Supervisor allowed (200)")
    void shouldEnforceRoleBasedAccessControlOnDirectoryEndpoints() throws Exception {
        // 1. Constable accessing un-scoped directory -> 403 Forbidden
        mockMvc.perform(get("/api/v1/officers")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 2. Constable attempting cache eviction -> 403 Forbidden
        mockMvc.perform(post("/api/v1/officers/cache/evict")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 3. Supervisor accessing directory -> 200 OK
        mockMvc.perform(get("/api/v1/officers")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Supervisor evicting directory cache -> 204 No Content
        mockMvc.perform(post("/api/v1/officers/cache/evict")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Controller Scoped Directory: Available officers per team requires authorized team role")
    void shouldEnforceTeamRoleOnScopedAvailableOfficersEndpoint() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Scope-Test-Team-" + UUID.randomUUID(), "Testing team scope"));

        // Senior Planner is added to the team
        teamService.addMember(team.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));

        // 1. Senior Planner querying available officers for their team -> 200 OK
        mockMvc.perform(get("/api/v1/officers/available")
                        .param("teamId", team.id().toString())
                        .header("Authorization", "Bearer " + plannerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 2. Constable who is NOT in the team querying available officers -> 403 Forbidden
        mockMvc.perform(get("/api/v1/officers/available")
                        .param("teamId", team.id().toString())
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not authorized to view available officers for this team"));

        // 3. Constable querying un-scoped available officers (no teamId) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/officers/available")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only supervisors can view the full un-scoped directory of available officers"));
    }

    @Test
    @Order(4)
    @DisplayName("1.4 Controller Parameter Validation: Malformed UUID teamId returns 400 Type Mismatch")
    void shouldRejectMalformedTeamIdWith400() throws Exception {
        mockMvc.perform(get("/api/v1/officers/available")
                        .param("teamId", "invalid-uuid-format-here")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Type Mismatch"))
                .andExpect(jsonPath("$.message").value("Parameter 'teamId' should be of type 'UUID'"));
    }

    // =========================================================================
    // SECTION 2: SERVICE LAYER - FILTERING, AUTO-SYNC & CACHE MANAGEMENT
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("2.1 Service Filtering: Available officers accurately excludes existing team members")
    void shouldFilterOutExistingTeamMembersFromAvailableDirectory() {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Filter-Test-Team-" + UUID.randomUUID(), "Filtering validation"));

        // Add Constable to team
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        // Query available officers for this team
        List<OfficerDirectoryDto> available = directoryService.getAvailableOfficersForTeam(team.id());
        assertThat(available).isNotEmpty();

        // Verify Constable is EXCLUDED from available roster
        boolean containsConstable = available.stream()
                .anyMatch(o -> o.officerId().equals(constableOfficer.getId()));
        assertThat(containsConstable)
                .as("Assigned team member must be excluded from available officer directory")
                .isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("2.2 Service Auto-Sync: Automatically provisions missing OfficerProfile from MySQL User")
    void shouldAutoProvisionMissingOfficerProfileOnDirectoryFetch() {
        // Create user in MySQL without creating OfficerProfile in PostgreSQL
        User unprovisionedUser = createTestUser("unprov_" + UUID.randomUUID() + "@police.gov", "Sub Inspector", officerRole);

        // Fetch directory (triggers auto-sync)
        directoryService.evictDirectoryCache();
        List<OfficerDirectoryDto> directory = directoryService.getAllOfficersCached();

        // Verify user was provisioned into PostgreSQL OfficerProfile and present in directory
        Optional<OfficerDirectoryDto> match = directory.stream()
                .filter(o -> o.mysqlUserId().equals(unprovisionedUser.getId()))
                .findFirst();

        assertThat(match).isPresent();
        assertThat(match.get().name()).isEqualTo("Officer Sub Inspector");
        assertThat(match.get().designation()).isEqualTo("Sub Inspector");

        // Verify physical row exists in PostgreSQL
        Optional<OfficerProfile> profile = officerRepository.findByMysqlUserId(unprovisionedUser.getId());
        assertThat(profile).isPresent();
    }

    @Test
    @Order(7)
    @DisplayName("2.3 Service Cache Eviction: Evicting cache clears Redis and next call reloads from DB")
    void shouldEvictAndTransparentlyRebuildRedisDirectoryCache() {
        // 1. Warm cache
        List<OfficerDirectoryDto> firstFetch = directoryService.getAllOfficersCached();
        assertThat(firstFetch).isNotEmpty();
        assertThat(cacheManager.getCache("officers_directory").get("all")).isNotNull();

        // 2. Evict cache
        directoryService.evictDirectoryCache();
        assertThat(cacheManager.getCache("officers_directory").get("all")).isNull();

        // 3. Second call reloads and re-caches
        List<OfficerDirectoryDto> secondFetch = directoryService.getAllOfficersCached();
        assertThat(secondFetch).isNotEmpty();
        assertThat(cacheManager.getCache("officers_directory").get("all")).isNotNull();
    }

    // =========================================================================
    // SECTION 3: SERVER PRESSURE, CONCURRENCY & CACHE STAMPEDE
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("3.1 Server Pressure: 15 concurrent cold-cache requests handled without duplicate keys or deadlocks")
    void shouldHandleConcurrentDirectoryRequestsOnColdCache() throws Exception {
        // Force cold cache
        directoryService.evictDirectoryCache();

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult res = mockMvc.perform(get("/api/v1/officers")
                                    .header("Authorization", "Bearer " + supervisorToken))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCounter.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean done = completionGate.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(done).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount);
    }

    @Test
    @Order(9)
    @DisplayName("3.2 Concurrency: Concurrent available officer queries while memberships are actively added")
    void shouldHandleConcurrentAvailabilityQueriesDuringRosterMutations() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Concurrent-Avail-Team-" + UUID.randomUUID(), "Stress available"));

        // Pre-create 5 officers that will be added to the team concurrently
        List<OfficerProfile> officersToAdd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User u = createTestUser("roster_mutate_" + i + "_" + UUID.randomUUID() + "@police.gov", "Officer", officerRole);
            officersToAdd.add(createTestOfficer(u, "Officer"));
        }

        // Pre-warm directory cache so concurrent queries leverage caching
        directoryService.getAllOfficersCached();

        int readQueries = 10;
        int writeMutations = officersToAdd.size();
        int totalWorkers = readQueries + writeMutations;

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(totalWorkers);
        AtomicInteger querySuccessCounter = new AtomicInteger(0);
        AtomicInteger mutationSuccessCounter = new AtomicInteger(0);

        // Submit concurrent read workers
        for (int i = 0; i < readQueries; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    authenticate(supervisorUser);
                    List<OfficerDirectoryDto> avail = directoryService.getAvailableOfficersForTeam(team.id());
                    if (avail != null) {
                        querySuccessCounter.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    SecurityContextHolder.clearContext();
                    completionGate.countDown();
                }
            });
        }

        // Submit concurrent write/roster mutation workers
        for (OfficerProfile officer : officersToAdd) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    authenticate(supervisorUser);
                    teamService.addMember(team.id(), new AddTeamMemberRequest(officer.getId(), TeamRole.FIELD_OPERATOR));
                    mutationSuccessCounter.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    SecurityContextHolder.clearContext();
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean done = completionGate.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(done).isTrue();
        assertThat(querySuccessCounter.get()).isEqualTo(readQueries);
        assertThat(mutationSuccessCounter.get()).isEqualTo(writeMutations);
    }

    // =========================================================================
    // SECTION 4: LAYER BLOCKING & FAULT INJECTION ACROSS DIRECTORY SUBSYSTEM
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("4.1 Block Controller: Malformed query parameter blocked before Service call")
    void shouldBlockAtControllerLayerOnMalformedQueryParam() throws Exception {
        mockMvc.perform(get("/api/v1/officers/available")
                        .param("teamId", "invalid-parameter-format")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Type Mismatch"));
    }

    @Test
    @Order(11)
    @DisplayName("4.2 Block Authorization: Non-authorized officer blocked from accessing un-scoped directory")
    void shouldBlockAtAuthorizationLayerForUnauthorizedOfficer() throws Exception {
        mockMvc.perform(get("/api/v1/officers")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @Order(12)
    @DisplayName("4.3 Block Redis: Corrupt cache entry detected, evicted and rebuilt from direct database")
    void shouldRecoverGracefullyWhenRedisCacheEntryIsCorrupted() {
        // Populate corrupted data in Redis cache key
        String cacheKey = "officers_directory::all";
        redisTemplate.opsForValue().set(cacheKey, "CORRUPTED_CACHE_VALUE_NON_LIST", Duration.ofSeconds(10));

        try {
            // Spring Cache attempts to cast String to List and throws ClassCastException
            assertThatThrownBy(() -> directoryService.getAllOfficersCached())
                    .isInstanceOf(ClassCastException.class);

            // Self-healing / recovery: Evict corrupt cache entry
            directoryService.evictDirectoryCache();

            // Directory service safely rebuilds fresh cache directly from database
            List<OfficerDirectoryDto> officers = directoryService.getAllOfficersCached();
            assertThat(officers).isNotNull();
            assertThat(cacheManager.getCache("officers_directory").get("all")).isNotNull();
        } finally {
            redisTemplate.delete(cacheKey);
            directoryService.evictDirectoryCache();
        }
    }
}
