package com.substring.auth.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.dtos.ApiError;
import com.substring.auth.app.officer.dto.OfficerDirectoryDto;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.service.OfficerDirectoryService;
import com.substring.auth.app.team.dto.*;
import com.substring.auth.app.team.entity.Team;
import com.substring.auth.app.team.entity.TeamAuditLog;
import com.substring.auth.app.team.entity.TeamMembership;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.repository.TeamAuditLogRepository;
import com.substring.auth.app.team.repository.TeamMembershipRepository;
import com.substring.auth.app.team.repository.TeamRepository;
import com.substring.auth.app.team.service.TeamAuthorizationService;
import com.substring.auth.app.team.service.TeamService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
@DisplayName("Configuration Destruction, Layer-by-Layer Blocking & Chaos Integration Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigurationDestructionAndChaosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("mysqlTransactionManager")
    private PlatformTransactionManager mysqlTransactionManager;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Autowired
    @Qualifier("mysqlEntityManagerFactory")
    private EntityManagerFactory mysqlEntityManagerFactory;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory postgresEntityManagerFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisCacheManager cacheManager;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private OfficerDirectoryService officerDirectoryService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamAuthorizationService teamAuthorizationService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository membershipRepository;

    @Autowired
    private TeamAuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private JwtService jwtService;

    private User supervisorUser;
    private User constableUser;
    private OfficerProfile supervisorOfficer;
    private OfficerProfile constableOfficer;
    private String supervisorToken;
    private String constableToken;

    private Role supervisorRole;
    private Role officerRole;

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

        supervisorUser = createTestUser("sup_chaos_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        constableUser = createTestUser("const_chaos_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

        supervisorOfficer = createTestOfficer(supervisorUser, "Commander");
        constableOfficer = createTestOfficer(constableUser, "Constable");

        supervisorToken = jwtService.generateAccessToken(supervisorUser);
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
    // PART A: CONFIGURATION DESTRUCTION & RESILIENCE TESTING
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("A.1 Destroy & Recover MySQL Config: Corrupt query failure, connection recovery and rollback")
    void testDestroyAndRecoverMySqlConfig() throws Exception {
        // 1. Simulate failure: execute corrupt query on MySQL
        assertThatThrownBy(() -> {
            try (Connection conn = mysqlDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM non_existent_mysql_table_chaos")) {
                stmt.executeQuery();
            }
        }).isInstanceOf(SQLException.class);

        // 2. Verify pool resilience: next connection is completely healthy
        try (Connection healthyConn = mysqlDataSource.getConnection()) {
            assertThat(healthyConn.isValid(2)).isTrue();
        }

        // 3. Test MySQL transaction rollback: simulated failure leaves zero orphaned users
        String rollbackEmail = "rollback_" + UUID.randomUUID() + "@police.gov";
        TransactionTemplate tx = new TransactionTemplate(mysqlTransactionManager);
        try {
            tx.execute(status -> {
                User u = new User();
                u.setEmail(rollbackEmail);
                u.setPolicePost("Post HQ");
                u.setPassword("hashedpassword");
                u.setRole(officerRole);
                userRepository.save(u);
                throw new RuntimeException("Deliberate MySQL Transaction Crash");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Deliberate MySQL Transaction Crash");
        }

        // Verify entity was NOT committed
        assertThat(userRepository.findByEmail(rollbackEmail)).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("A.2 Destroy & Recover Postgres Config: Corrupt query failure, connection recovery and rollback")
    void testDestroyAndRecoverPostgresConfig() throws Exception {
        // 1. Simulate failure: execute corrupt query on PostgreSQL
        assertThatThrownBy(() -> {
            try (Connection conn = postgresDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM non_existent_postgres_table_chaos")) {
                stmt.executeQuery();
            }
        }).isInstanceOf(SQLException.class);

        // 2. Verify pool resilience: next connection is completely healthy
        try (Connection healthyConn = postgresDataSource.getConnection()) {
            assertThat(healthyConn.isValid(2)).isTrue();
        }

        // 3. Test PostgreSQL transaction rollback: simulated failure leaves zero orphaned teams
        String rollbackTeamName = "Rollback-Team-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(postgresTransactionManager);
        try {
            tx.execute(status -> {
                Team t = new Team();
                t.setName(rollbackTeamName);
                t.setDescription("Must rollback");
                teamRepository.save(t);
                throw new RuntimeException("Deliberate Postgres Transaction Crash");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Deliberate Postgres Transaction Crash");
        }

        // Verify entity was NOT committed
        assertThat(teamRepository.findByName(rollbackTeamName)).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("A.3 Destroy & Recover Redis Config: Corrupted payload, manual flush and cache rebuilding")
    void testDestroyAndRecoverRedisConfig() {
        String testKey = "officer:live:" + UUID.randomUUID();

        // 1. Corrupt cache payload with an unparseable non-object value
        redisTemplate.opsForValue().set(testKey, "MALFORMED_NON_JSON_CORRUPTED_STRING", Duration.ofSeconds(30));

        // Read does not crash the application
        Object cachedValue = redisTemplate.opsForValue().get(testKey);
        assertThat(cachedValue).isNotNull();
        redisTemplate.delete(testKey);

        // 2. Flush directory cache, test rebuild
        officerDirectoryService.evictDirectoryCache();
        List<OfficerDirectoryDto> rebuilt = officerDirectoryService.getAllOfficersCached();
        assertThat(rebuilt).isNotNull();
        assertThat(cacheManager.getCache("officers_directory").get("all")).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("A.4 Project Config Resilience: ModelMapper null and object mapping safety")
    void testProjectConfigResilience() {
        assertThat(modelMapper).isNotNull();

        // Safe conversion of valid bean
        User u = new User();
        u.setName("Test Officer");
        u.setEmail("test@police.gov");

        OfficerProfile op = modelMapper.map(u, OfficerProfile.class);
        assertThat(op).isNotNull();

        // Null source safely handled
        assertThatThrownBy(() -> modelMapper.map(null, OfficerProfile.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Order(5)
    @DisplayName("A.5 APIDoc Config Resilience: Swagger OpenAPI endpoint serves specification")
    void testApiDocConfigResilience() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("COPMAP APPLICATION BUILT BY ANANT GUPTA."));
    }

    // =========================================================================
    // PART B: TEAM MODULE - FULL PIPELINE & LAYER-BY-LAYER BLOCKING
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("B.1 Full Pipeline under Server Pressure: 25 simultaneous multi-step operations")
    void testFullPipelineUnderServerPressure() throws Exception {
        int concurrentUsers = 25;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(concurrentUsers);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < concurrentUsers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();

                    // Step 1: Controller -> Service -> Repo -> Audit: Create Team
                    CreateTeamRequest createReq = new CreateTeamRequest("Pressure-Team-" + index + "-" + UUID.randomUUID(), "Heavy load test");
                    MvcResult createRes = mockMvc.perform(post("/api/v1/teams")
                                    .header("Authorization", "Bearer " + supervisorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(createReq)))
                            .andReturn();

                    if (createRes.getResponse().getStatus() == 201) {
                        TeamResponse tr = objectMapper.readValue(createRes.getResponse().getContentAsString(), TeamResponse.class);

                        // Step 2: Query Team Members
                        mockMvc.perform(get("/api/v1/teams/" + tr.id() + "/members")
                                        .header("Authorization", "Bearer " + supervisorToken))
                                .andExpect(status().isOk());

                        // Step 3: Query Team Audit History
                        mockMvc.perform(get("/api/v1/teams/" + tr.id() + "/audit")
                                        .header("Authorization", "Bearer " + supervisorToken))
                                .andExpect(status().isOk());

                        successCounter.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completedInTime = completionGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();
        assertThat(successCounter.get()).isEqualTo(concurrentUsers);
    }

    @Test
    @Order(7)
    @DisplayName("B.2 Block Layer 1 [Controller]: Intercept unparseable path variable before Service")
    void testBlockControllerLayer() throws Exception {
        // Nature & Response: Malformed UUID in URI path is blocked immediately at Controller level
        mockMvc.perform(get("/api/v1/teams/NOT-A-VALID-UUID/members")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Type Mismatch"))
                .andExpect(jsonPath("$.message").value("Parameter 'teamId' should be of type 'UUID'"));
    }

    @Test
    @Order(8)
    @DisplayName("B.3 Block Layer 2 [Authorization]: Halt execution when unauthenticated or lacking role")
    void testBlockAuthorizationLayer() throws Exception {
        CreateTeamRequest req = new CreateTeamRequest("Blocked-Auth-Team-" + UUID.randomUUID(), "Authorization block test");

        // 1. Block: Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 2. Block: Constable attempting team creation -> 403 Forbidden
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only supervisors can create teams"));

        // 3. Programmatic check: TeamAuthorizationService throws AccessDeniedException without Supervisor authority
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> teamAuthorizationService.requireSupervisor("Only supervisors allowed"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only supervisors allowed");
    }

    @Test
    @Order(9)
    @DisplayName("B.4 Block Layer 3 [Service]: Business validation stops duplicate team before repository call")
    void testBlockServiceLayer() {
        authenticate(supervisorUser);
        String teamName = "Service-Blocked-Team-" + UUID.randomUUID();

        // 1. Initial creation succeeds
        teamService.createTeam(new CreateTeamRequest(teamName, "First instance"));

        // 2. Duplicate creation is blocked by Service layer validation
        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(teamName, "Duplicate instance")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A team with this name already exists");
    }

    @Test
    @Order(10)
    @DisplayName("B.5 Block Layer 4 [TeamRepository]: Database unique constraint stops duplicate team name")
    void testBlockTeamRepositoryLayer() {
        String constrainedName = "Repo-Blocked-Team-" + UUID.randomUUID();

        Team t1 = new Team();
        t1.setName(constrainedName);
        t1.setDescription("Base instance");
        teamRepository.saveAndFlush(t1);

        Team t2 = new Team();
        t2.setName(constrainedName);
        t2.setDescription("Constraint violation candidate");

        // Bypassing Service layer: directly invoking Repository saveAndFlush is blocked by PostgreSQL unique constraint
        assertThatThrownBy(() -> teamRepository.saveAndFlush(t2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(11)
    @DisplayName("B.6 Block Layer 5 [TeamMembershipRepository]: Database constraint uq_team_officer blocks duplicates")
    void testBlockTeamMembershipRepositoryLayer() {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Membership-Block-Team-" + UUID.randomUUID(), "Membership constraint"));
        Team teamEntity = teamRepository.findById(team.id()).orElseThrow();

        TeamMembership m1 = new TeamMembership();
        m1.setTeam(teamEntity);
        m1.setOfficer(constableOfficer);
        m1.setTeamRole(TeamRole.FIELD_OPERATOR);
        membershipRepository.saveAndFlush(m1);

        TeamMembership m2 = new TeamMembership();
        m2.setTeam(teamEntity);
        m2.setOfficer(constableOfficer);
        m2.setTeamRole(TeamRole.TEAM_LEAD);

        // Bypassing Service layer: direct repository save is blocked by uq_team_officer constraint
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(m2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(12)
    @DisplayName("B.7 Block Layer 6 [TeamAuditLogRepository]: Verify atomic rollback of team and audit on failure")
    void testBlockAuditLogRepositoryAtomicity() {
        authenticate(supervisorUser);
        String atomicTeamName = "Atomic-Chaos-Team-" + UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(postgresTransactionManager);

        try {
            tx.execute(status -> {
                Team team = new Team();
                team.setName(atomicTeamName);
                team.setDescription("Atomic chaos candidate");
                Team savedTeam = teamRepository.save(team);

                TeamAuditLog audit = new TeamAuditLog();
                audit.setTeam(savedTeam);
                audit.setChangedBy(supervisorOfficer);
                audit.setAction("CHAOS_AUDIT_FAIL");
                auditLogRepository.save(audit);

                // Simulate runtime crash before transaction commit
                throw new IllegalStateException("Deliberate crash to verify all-or-nothing atomicity");
            });
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Deliberate crash to verify all-or-nothing atomicity");
        }

        // Verify that NEITHER the Team NOR the Audit Log exists in PostgreSQL
        assertThat(teamRepository.findByName(atomicTeamName)).isEmpty();

        long auditCount = auditLogRepository.findAll().stream()
                .filter(l -> "CHAOS_AUDIT_FAIL".equals(l.getAction()))
                .count();
        assertThat(auditCount).isEqualTo(0);
    }
}
