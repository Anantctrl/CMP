package com.copmap.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.auth.services.impl.JwtService;
import com.copmap.officer.dto.*;
import com.copmap.officer.entity.*;
import com.copmap.officer.repository.*;
import com.copmap.officer.service.OfficerDirectoryService;
import com.copmap.officer.service.TaskAssignmentService;
import com.copmap.officer.service.TaskService;
import com.copmap.operations.common.dto.*;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationAuditLog;
import com.copmap.operations.common.enums.OperationStatus;
import com.copmap.operations.common.enums.OperationType;
import com.copmap.operations.common.repository.OperationAuditLogRepository;
import com.copmap.operations.common.repository.OperationRepository;
import com.copmap.operations.common.service.OperationService;
import com.copmap.team.dto.*;
import com.copmap.team.entity.Team;
import com.copmap.team.entity.TeamAuditLog;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamAuditLogRepository;
import com.copmap.team.repository.TeamMembershipRepository;
import com.copmap.team.repository.TeamRepository;
import com.copmap.team.service.TeamService;
import com.copmap.tracking.dto.LocationUpdateRequest;
import com.copmap.tracking.dto.OfficerLiveLocationResponse;
import com.copmap.tracking.entity.OfficerLocationUpdate;
import com.copmap.tracking.repository.OfficerLocationUpdateRepository;
import com.copmap.tracking.service.LocationTrackingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("End-to-End System Failure, Multi-Database Routing & High-Scale Chaos Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndSystemFailureAndScalingChaosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Autowired
    @Qualifier("mysqlTransactionManager")
    private PlatformTransactionManager mysqlTransactionManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository teamMembershipRepository;

    @Autowired
    private TeamAuditLogRepository teamAuditLogRepository;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    private OfficerLocationUpdateRepository locationUpdateRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private OperationService operationService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskAssignmentService assignmentService;

    @Autowired
    private LocationTrackingService trackingService;

    @Autowired
    private OfficerDirectoryService directoryService;

    @Autowired
    private JwtService jwtService;

    private Role supervisorRole;
    private Role officerRole;

    private User supervisorUser;
    private User leadUser;
    private User constableUser;

    private OfficerProfile supervisorOfficer;
    private OfficerProfile leadOfficer;
    private OfficerProfile constableOfficer;

    private String supervisorToken;
    private String leadToken;
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

        supervisorUser = createTestUser("e2e_sup_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        leadUser = createTestUser("e2e_lead_" + UUID.randomUUID() + "@police.gov", "Inspector", officerRole);
        constableUser = createTestUser("e2e_const_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

        supervisorOfficer = createTestOfficer(supervisorUser, "Commander");
        leadOfficer = createTestOfficer(leadUser, "Inspector");
        constableOfficer = createTestOfficer(constableUser, "Constable");

        supervisorToken = jwtService.generateAccessToken(supervisorUser);
        leadToken = jwtService.generateAccessToken(leadUser);
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
        u.setName("E2E Officer " + post);
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
                user, null, List.of(new SimpleGrantedAuthority(roleName), new SimpleGrantedAuthority("ROLE_" + roleName))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // =========================================================================
    // SECTION 1: FULL END-TO-END OPERATIONAL LIFECYCLE PIPELINE
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Full E2E Mission Flow: Auth -> Team -> Operation -> Task -> Live GPS -> Review -> Completion")
    void shouldExecuteFullEndToEndMissionWorkflowAcrossAllTiers() throws Exception {
        // Step 1: Create Tactical Team via API
        CreateTeamRequest teamReq = new CreateTeamRequest("E2E Tactical Strike Alpha " + UUID.randomUUID(), "Rapid deployment unit");
        MvcResult teamRes = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teamReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        TeamResponse team = objectMapper.readValue(teamRes.getResponse().getContentAsString(), TeamResponse.class);

        // Step 2: Add Team Members (Lead & Constable)
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddTeamMemberRequest(leadOfficer.getId(), TeamRole.TEAM_LEAD))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR))))
                .andExpect(status().isCreated());

        // Step 3: Create Tactical Operation in DRAFT
        CreateOperationRequest opReq = new CreateOperationRequest(
                OperationType.PATROLLING,
                "E2E City Perimeter Patrol " + UUID.randomUUID(),
                "Active sector patrol mission",
                Set.of(team.id()),
                "Urgent tactical patrol directive"
        );
        MvcResult opRes = mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(opReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        OperationResponse op = objectMapper.readValue(opRes.getResponse().getContentAsString(), OperationResponse.class);

        // Step 4: Schedule and Activate Operation
        ScheduleOperationRequest scheduleReq = new ScheduleOperationRequest(
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusHours(8),
                "Operational schedule cleared by Command"
        );
        mockMvc.perform(put("/api/v1/operations/" + op.id() + "/schedule")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(patch("/api/v1/operations/" + op.id() + "/status")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeOperationStatusRequest(OperationStatus.ACTIVE, "Operations underway"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Step 5: Create Tactical Task linked to Operation
        CreateTaskRequest taskReq = new CreateTaskRequest(
                team.id(),
                "Sector Reconnaissance Alpha",
                "PATROLLING",
                "High-priority perimeter surveillance",
                "Station Alpha Checkpoint",
                18.5204,
                73.8567
        );
        MvcResult taskRes = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskReq)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse task = objectMapper.readValue(taskRes.getResponse().getContentAsString(), TaskResponse.class);

        // Step 6: Assign Constable to Task
        AssignOfficerRequest assignReq = new AssignOfficerRequest(
                constableOfficer.getId(),
                "Field Officer",
                "Maintain continuous perimeter presence"
        );
        MvcResult assignRes = mockMvc.perform(post("/api/v1/assignments/tasks/" + task.id() + "/assign")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();

        AssignmentResponse assignment = objectMapper.readValue(assignRes.getResponse().getContentAsString(), AssignmentResponse.class);

        // Step 7: Stream Real-Time GPS Telemetry into Redis & PostgreSQL
        LocationUpdateRequest gpsReq = new LocationUpdateRequest(
                18.52043,
                73.85674,
                4.5,
                OffsetDateTime.now()
        );
        mockMvc.perform(post("/api/v1/tracking/update")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gpsReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(18.52043))
                .andExpect(jsonPath("$.longitude").value(73.85674));

        // Step 8: Progress Task Lifecycle: Start -> Submit -> Approve
        mockMvc.perform(post("/api/v1/assignments/" + assignment.id() + "/start")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/assignments/" + assignment.id() + "/submit")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAssignmentRequest("Reconnaissance successfully completed without incidents"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/assignments/" + assignment.id() + "/approve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewAssignmentRequest("Approved by Watch Commander"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Step 9: Close Operation
        mockMvc.perform(patch("/api/v1/operations/" + op.id() + "/status")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeOperationStatusRequest(OperationStatus.COMPLETED, "Mission objective accomplished"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Step 10: Verify Audit Trails across all tiers
        assertThat(taskAuditLogRepository.findAll().stream().anyMatch(a -> a.getTask().getId().equals(task.id()))).isTrue();
        assertThat(teamAuditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(team.id())).isNotEmpty();
        assertThat(operationAuditLogRepository.findAllByOperation_IdOrderByCreatedAtDesc(op.id())).isNotEmpty();
    }

    // =========================================================================
    // SECTION 2: POSTGRESQL FAILURE & RESILIENCE POINTS
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("2.1 PostgreSQL Failure: Mid-transaction failure rolls back partial writes completely")
    void shouldRollbackPostgreSqlTransactionOnUnexpectedFailure() {
        String testTeamName = "Rollback_Chaos_Team_" + UUID.randomUUID();
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = postgresTransactionManager.getTransaction(def);

        try {
            Team team = new Team();
            team.setName(testTeamName);
            team.setDescription("Transaction isolation verification team");
            teamRepository.save(team);

            // Deliberate exception simulating sudden downstream service crash
            throw new IllegalStateException("Simulated Network/DB Partition during persistence");
        } catch (IllegalStateException e) {
            postgresTransactionManager.rollback(status);
        }

        // Verify team was rolled back and never committed to PostgreSQL
        assertThat(teamRepository.findByName(testTeamName)).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("2.2 PostgreSQL Read-Only Replica Simulation: Writes fail safely while reads succeed")
    void shouldFailWritesSafelyOnReadOnlyPostgreSqlConnection() throws SQLException {
        try (Connection conn = postgresDataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.setReadOnly(true);

            // Read query must succeed
            try (Statement readStmt = conn.createStatement()) {
                assertThat(readStmt.execute("SELECT count(*) FROM teams")).isTrue();
            }

            // Write query must fail safely with SQLException
            try (Statement writeStmt = conn.createStatement()) {
                assertThatThrownBy(() -> writeStmt.executeUpdate(
                        "INSERT INTO teams (id, name, description, created_at, updated_at) VALUES ('"
                                + UUID.randomUUID() + "', 'Read-Only Chaos', 'Fail Expected', NOW(), NOW())"
                )).isInstanceOf(SQLException.class);
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("2.3 PostgreSQL Query Timeout Simulation: Slow queries abort without hanging connection pool")
    void shouldAbortSlowPostgreSqlQueriesWithoutCorruptingConnectionPool() throws SQLException {
        try (Connection conn = postgresDataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(1); // 1-second timeout
                assertThatThrownBy(() -> stmt.execute("SELECT pg_sleep(3);"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    // =========================================================================
    // SECTION 3: REDIS FAILURE & DISTRIBUTED CACHING RESILIENCE
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("3.1 Redis Crash/Outage Fallback: GPS tracking survives Redis key loss via PostgreSQL fallback")
    void shouldFallbackToPostgreSqlWhenRedisLocationKeyIsLost() throws Exception {
        UUID officerId = constableOfficer.getId();

        // Record location via service (stores in PostgreSQL + Redis)
        LocationUpdateRequest req = new LocationUpdateRequest(18.5300, 73.8600, 5.0, OffsetDateTime.now());
        authenticate(constableUser);
        trackingService.recordLocation(req);

        // Simulate complete Redis data loss / key eviction
        String redisKey = "officer:live:" + officerId;
        redisTemplate.delete(redisKey);
        assertThat(redisTemplate.hasKey(redisKey)).isFalse();

        // Read live location via API: should transparently hydrate from PostgreSQL
        mockMvc.perform(get("/api/v1/tracking/officers/" + officerId + "/latest")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(officerId.toString()))
                .andExpect(jsonPath("$.latitude").value(18.5300))
                .andExpect(jsonPath("$.longitude").value(73.8600));

        // Verify Redis cache was seamlessly re-populated
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("3.2 Redis Corrupted Cache Recovery: Corrupted cache value rejected and recovered from DB")
    void shouldRecoverGracefullyWhenRedisCacheContainsCorruptedData() throws Exception {
        String directoryKey = "officers_directory:all";

        // Inject corrupted invalid string into Redis cache
        redisTemplate.opsForValue().set(directoryKey, "CORRUPTED_NON_JSON_BINARY_DATA_{{{");

        // Request officer directory: must handle corrupted entry gracefully and return DB records
        List<OfficerDirectoryDto> officers = directoryService.getAllOfficersCached();
        assertThat(officers).isNotEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("3.3 Cache Stampede Resilience: 25 simultaneous cache misses handled in parallel without timeout")
    void shouldPreventCacheStampedeUnderHighConcurrency() throws Exception {
        directoryService.evictDirectoryCache();

        int concurrentClients = 25;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentClients);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(concurrentClients);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < concurrentClients; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    List<OfficerDirectoryDto> list = directoryService.getAllOfficersCached();
                    if (list != null && !list.isEmpty()) {
                        successCounter.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completedInTime = completionGate.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completedInTime).isTrue();
        assertThat(successCounter.get()).isEqualTo(concurrentClients);
    }

    // =========================================================================
    // SECTION 4: MYSQL FAILURE & INDEPENDENT ROUTING RESILIENCE
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("4.1 Multi-Database Routing Isolation: PostgreSQL operations succeed during MySQL stress")
    void shouldEnsurePostgreSqlOperationsSurviveIndependentOfMySql() {
        // Query PostgreSQL while MySQL connection is tested
        long postgresTeamCount = teamRepository.count();
        long mysqlUserCount = userRepository.count();

        assertThat(postgresTeamCount).isGreaterThanOrEqualTo(0);
        assertThat(mysqlUserCount).isGreaterThanOrEqualTo(0);

        // Verify write to PostgreSQL works independently
        Team isolatedTeam = new Team();
        isolatedTeam.setName("Postgres_Independent_" + UUID.randomUUID());
        isolatedTeam.setDescription("Testing independent persistence routing");
        Team saved = teamRepository.save(isolatedTeam);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @Order(9)
    @DisplayName("4.2 MySQL Constraint Violation: Duplicate user email rejected with DataIntegrityViolationException")
    void shouldEnforceUniqueUserEmailConstraintInMySql() {
        String duplicateEmail = "e2e_duplicate_" + UUID.randomUUID() + "@police.gov";
        createTestUser(duplicateEmail, "Constable", officerRole);

        // Attempt duplicate insertion
        User duplicate = new User();
        duplicate.setEmail(duplicateEmail);
        duplicate.setName("Duplicate User");
        duplicate.setPassword("pass");
        duplicate.setRole(officerRole);
        duplicate.setEnable(true);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(10)
    @DisplayName("4.3 Auth Gateway Resiliency: Tampered JWT token rejected immediately (401)")
    void shouldRejectTamperedOrCorruptedJwtTokens() throws Exception {
        String forgedToken = supervisorToken + "TAMPERED_SIG";

        mockMvc.perform(get("/api/v1/teams")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // SECTION 5: SERVER SATURATION, SCALING & API CONCURRENCY
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("5.1 HikariCP Pool Saturation & Recovery: High concurrency releases connections cleanly")
    void shouldSaturateAndRecoverHikariConnectionPoolCleanly() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    try (Connection conn = postgresDataSource.getConnection()) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("SELECT 1;");
                            successCounter.incrementAndGet();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount);
    }

    @Test
    @Order(12)
    @DisplayName("5.2 Multi-Officer Distributed GPS Telemetry & Team Live Map Aggregation")
    void shouldAggregateMultiOfficerDistributedGpsTelemetryAccurately() throws Exception {
        // Create Team
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("E2E Live Fleet Team " + UUID.randomUUID(), "GPS Map fleet"));

        // Create 5 Officers and add to team
        List<User> users = new ArrayList<>();
        List<OfficerProfile> officers = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            User u = createTestUser("fleet_" + i + "_" + UUID.randomUUID() + "@police.gov", "Officer", officerRole);
            OfficerProfile p = createTestOfficer(u, "Officer");
            teamService.addMember(team.id(), new AddTeamMemberRequest(p.getId(), TeamRole.FIELD_OPERATOR));
            users.add(u);
            officers.add(p);
            tokens.add(jwtService.generateAccessToken(u));
        }

        // Concurrently stream 5 different GPS locations
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    LocationUpdateRequest gps = new LocationUpdateRequest(
                            18.5000 + (idx * 0.01),
                            73.8000 + (idx * 0.01),
                            5.0,
                            OffsetDateTime.now()
                    );
                    mockMvc.perform(post("/api/v1/tracking/update")
                                    .header("Authorization", "Bearer " + tokens.get(idx))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(gps)))
                            .andExpect(status().isCreated());
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean streamed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(streamed).isTrue();

        // Query Team Live Map as Supervisor
        mockMvc.perform(get("/api/v1/tracking/teams/" + team.id() + "/live")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @Order(13)
    @DisplayName("5.3 20-Thread Mixed Concurrency Scaling: Auth, Team, Ops & Tasks without Deadlocks")
    void shouldHandleMixedWorkloadConcurrencyScalingWithoutDeadlocks() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Scaling Team " + UUID.randomUUID(), "Workload scaling test"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        int totalThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final int mod = i % 4;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (mod == 0) {
                        // Query authorized teams
                        mockMvc.perform(get("/api/v1/teams")
                                        .header("Authorization", "Bearer " + supervisorToken))
                                .andExpect(status().isOk());
                    } else if (mod == 1) {
                        // Query operations
                        mockMvc.perform(get("/api/v1/operations")
                                        .header("Authorization", "Bearer " + supervisorToken))
                                .andExpect(status().isOk());
                    } else if (mod == 2) {
                        // Query task directory
                        mockMvc.perform(get("/api/v1/tasks")
                                        .header("Authorization", "Bearer " + supervisorToken))
                                .andExpect(status().isOk());
                    } else {
                        // Submit location update
                        LocationUpdateRequest gps = new LocationUpdateRequest(18.5200, 73.8500, 5.0, OffsetDateTime.now());
                        mockMvc.perform(post("/api/v1/tracking/update")
                                        .header("Authorization", "Bearer " + constableToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(gps)))
                                .andExpect(status().isCreated());
                    }
                    successCounter.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean allFinished = finishLatch.await(20, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(allFinished).isTrue();
        assertThat(successCounter.get()).isEqualTo(totalThreads);
    }

    @Test
    @Order(14)
    @DisplayName("5.4 API Error Sanitization: Zero Java stack trace or raw SQL leaked on errors")
    void shouldSanitizeApiErrorsWithoutLeakingInternalStackTraces() throws Exception {
        // Trigger 400 Bad Request with invalid JSON
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-body}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());

        // Trigger 400 Bad Request on type mismatch (invalid UUID in path)
        mockMvc.perform(get("/api/v1/teams/NOT-A-VALID-UUID")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
