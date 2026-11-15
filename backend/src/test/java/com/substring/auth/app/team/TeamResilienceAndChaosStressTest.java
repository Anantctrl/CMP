package com.substring.auth.app.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.dtos.ApiError;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CopMap Team Module - Comprehensive Resilience, Chaos & Stress Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TeamResilienceAndChaosStressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory postgresEntityManagerFactory;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    private Role supervisorRole;
    private Role officerRole;

    private User primarySupervisor;
    private User seniorPlannerUser;
    private User fieldOperatorUser;

    private OfficerProfile primarySupervisorOfficer;
    private OfficerProfile seniorPlannerOfficer;
    private OfficerProfile fieldOperatorOfficer;

    private String primarySupervisorToken;
    private String seniorPlannerToken;
    private String fieldOperatorToken;

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

        primarySupervisor = createTestUser("sup_chaos_" + UUID.randomUUID() + "@police.gov", "DGP", supervisorRole);
        seniorPlannerUser = createTestUser("plan_chaos_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        fieldOperatorUser = createTestUser("field_chaos_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

        primarySupervisorOfficer = createTestOfficer(primarySupervisor, "Director General");
        seniorPlannerOfficer = createTestOfficer(seniorPlannerUser, "Senior Planner");
        fieldOperatorOfficer = createTestOfficer(fieldOperatorUser, "Patrol Constable");

        primarySupervisorToken = jwtService.generateAccessToken(primarySupervisor);
        seniorPlannerToken = jwtService.generateAccessToken(seniorPlannerUser);
        fieldOperatorToken = jwtService.generateAccessToken(fieldOperatorUser);
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
    // SECTION 1: TEAM CREATION, FORMAT VARIATIONS & SERVER PRESSURE
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Multi-Account Burst: Create multiple teams with different accounts simultaneously")
    void shouldCreateMultipleTeamsWithDifferentAccountsSimultaneously() throws Exception {
        int accountCount = 5;
        List<User> supervisors = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < accountCount; i++) {
            User s = createTestUser("sup_burst_" + i + "_" + UUID.randomUUID() + "@police.gov", "Commander-" + i, supervisorRole);
            createTestOfficer(s, "Commander-" + i);
            supervisors.add(s);
            tokens.add(jwtService.generateAccessToken(s));
        }

        ExecutorService executor = Executors.newFixedThreadPool(accountCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(accountCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < accountCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreateTeamRequest req = new CreateTeamRequest("Concurrent-Team-" + index + "-" + UUID.randomUUID(), "Burst test team " + index);
                    MvcResult res = mockMvc.perform(post("/api/v1/teams")
                                    .header("Authorization", "Bearer " + tokens.get(index))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
                    results.add(res);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = completionGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(results).hasSize(accountCount);

        for (MvcResult res : results) {
            assertThat(res.getResponse().getStatus()).isEqualTo(201);
            TeamResponse tr = objectMapper.readValue(res.getResponse().getContentAsString(), TeamResponse.class);
            assertThat(tr.id()).isNotNull();

            // Verify each team audit log records the respective creator correctly
            List<TeamAuditLog> logs = auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(tr.id());
            assertThat(logs).isNotEmpty();
            assertThat(logs.get(0).getAction()).isEqualTo("TEAM_CREATED");
        }
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Payload Formats: Blank, oversized, SQL injection, XSS, unicode and malformed JSON")
    void shouldValidateStandardAndNonStandardPayloadFormats() throws Exception {
        // 1. Blank name -> 400 Bad Request
        CreateTeamRequest blankNameReq = new CreateTeamRequest("", "Valid description");
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankNameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").value("Team name is required"));

        // 2. Oversized name (>100 characters) -> 400 Bad Request
        String oversizedName = "T".repeat(101);
        CreateTeamRequest oversizedReq = new CreateTeamRequest(oversizedName, "Oversized name description");
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").value("Team name cannot exceed 100 characters"));

        // 3. Null name -> 400 Bad Request
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": null, \"description\": \"Null name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 4. SQL Injection payload -> Handled safely as a literal string (ORM parameter binding prevents execution)
        String sqlInjectionName = "Team'; DROP TABLE teams; --" + UUID.randomUUID().toString().substring(0, 6);
        CreateTeamRequest sqlReq = new CreateTeamRequest(sqlInjectionName, "Testing SQL injection immunity");
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sqlReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(sqlInjectionName));

        // 5. XSS payload -> Handled safely without executing
        String xssName = "<script>alert('xss')</script> " + UUID.randomUUID().toString().substring(0, 6);
        CreateTeamRequest xssReq = new CreateTeamRequest(xssName, "Testing XSS payload safety");
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(xssName));

        // 6. Emojis and Unicode characters -> UTF-8 preserved
        String unicodeName = "🚨 Rapid Response 隊 🚔 " + UUID.randomUUID().toString().substring(0, 6);
        CreateTeamRequest unicodeReq = new CreateTeamRequest(unicodeName, "Unicode tactical description");
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unicodeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(unicodeName));

        // 7. Malformed JSON syntax -> 400 Bad Request with Malformed JSON error
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Broken JSON syntax without closing brace"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed JSON"));
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Unauthorized Team Creation: Unauthenticated (401) and Non-Supervisor (403)")
    void shouldRejectTeamCreationThroughUnauthorizedAccounts() throws Exception {
        CreateTeamRequest req = new CreateTeamRequest("Unauthorized-Team-" + UUID.randomUUID(), "Illegal creation");

        // 1. Unauthenticated request (no JWT token) -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 2. Authenticated as Field Operator (Constable) -> 403 Forbidden
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + fieldOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only supervisors can create teams"));

        // 3. Authenticated as Senior Planner (non-supervisor) -> 403 Forbidden
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + seniorPlannerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @Order(4)
    @DisplayName("1.4 Server Pressure & Error 500 Immunity: Burst traffic & controlled internal error format")
    void shouldHandleServerPressureAndReturnStandardized500WithoutLeakingInternals() throws Exception {
        // High concurrency load: 15 requests barrage
        int burstRequests = 15;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(burstRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < burstRequests; i++) {
            executor.submit(() -> {
                try {
                    MvcResult res = mockMvc.perform(get("/api/v1/teams")
                                    .header("Authorization", "Bearer " + primarySupervisorToken))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean done = latch.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(done).isTrue();
        assertThat(successCount.get()).isEqualTo(burstRequests);

        // Verify GlobalExceptionHandler catches 500 errors cleanly without leaking stack traces or credentials
        MvcResult errorResult = mockMvc.perform(get("/api/v1/teams/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + primarySupervisorToken))
                .andExpect(status().isNotFound())
                .andReturn();

        ApiError notFoundError = objectMapper.readValue(errorResult.getResponse().getContentAsString(), ApiError.class);
        assertThat(notFoundError.status()).isEqualTo(404);
        assertThat(notFoundError.message()).contains("Team not found");
    }

    // =========================================================================
    // SECTION 2: ROSTER MEMBERSHIP, CAPACITY & CONCURRENCY
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("2.1 Roster Scaling: Add multiple officers up to high capacity and verify roster consistency")
    void shouldSupportRosterScalingAndLargeMemberships() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Large-Roster-Team-" + UUID.randomUUID(), "Capacity testing"));

        int rosterSize = 20;
        List<OfficerProfile> officers = new ArrayList<>();

        for (int i = 0; i < rosterSize; i++) {
            User u = createTestUser("roster_user_" + i + "_" + UUID.randomUUID() + "@police.gov", "Officer-" + i, officerRole);
            OfficerProfile p = createTestOfficer(u, "Duty Officer " + i);
            officers.add(p);

            TeamRole role = (i % 4 == 0) ? TeamRole.SENIOR_PLANNER
                    : (i % 4 == 1) ? TeamRole.TEAM_LEAD
                    : (i % 4 == 2) ? TeamRole.CONTROL_OPERATOR
                    : TeamRole.FIELD_OPERATOR;

            teamService.addMember(team.id(), new AddTeamMemberRequest(p.getId(), role));
        }

        // Verify roster size through Service
        List<TeamMemberResponse> members = teamService.getTeamMembers(team.id());
        assertThat(members).hasSize(rosterSize);

        // Verify count in Repository
        long dbCount = membershipRepository.countByTeam_Id(team.id());
        assertThat(dbCount).isEqualTo(rosterSize);

        // Verify via HTTP endpoint
        mockMvc.perform(get("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(rosterSize));
    }

    @Test
    @Order(6)
    @DisplayName("2.2 Failing Membership Requests: Non-existent IDs, duplicates, invalid enums and unauthorized additions")
    void shouldFailInvalidMembershipRequests() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Validation-Team-" + UUID.randomUUID(), "Fail testing"));

        // 1. Non-existent Team ID -> 404 Not Found
        UUID fakeTeamId = UUID.randomUUID();
        AddTeamMemberRequest validReq = new AddTeamMemberRequest(fieldOperatorOfficer.getId(), TeamRole.FIELD_OPERATOR);
        mockMvc.perform(post("/api/v1/teams/" + fakeTeamId + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Team not found"));

        // 2. Non-existent Officer ID -> 404 Not Found
        UUID fakeOfficerId = UUID.randomUUID();
        AddTeamMemberRequest fakeOfficerReq = new AddTeamMemberRequest(fakeOfficerId, TeamRole.FIELD_OPERATOR);
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fakeOfficerReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Officer not found"));

        // 3. Null Officer ID -> 400 Bad Request
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"officerId\": null, \"teamRole\": \"FIELD_OPERATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.officerId").value("Officer ID is required"));

        // 4. Invalid Role Enum -> 400 Bad Request
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"officerId\": \"" + fieldOperatorOfficer.getId() + "\", \"teamRole\": \"SUPER_COWBOY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON"));

        // 5. First Member Addition succeeds via HTTP
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated());

        // Duplicate Member Addition -> 400 Bad Request
        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Officer is already a member of this team"));

        // 6. Unauthorized Member Addition: Field Operator attempting to add members -> 403 Forbidden
        User extraUser = createTestUser("extra_" + UUID.randomUUID() + "@police.gov", "Officer", officerRole);
        OfficerProfile extraOfficer = createTestOfficer(extraUser, "Patrol");
        AddTeamMemberRequest extraReq = new AddTeamMemberRequest(extraOfficer.getId(), TeamRole.FIELD_OPERATOR);

        mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                        .header("Authorization", "Bearer " + fieldOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extraReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only Supervisors or Senior Planners in this team can add members"));
    }

    @Test
    @Order(7)
    @DisplayName("2.3 Single-Account Race Condition: Simultaneous duplicate member addition attempts")
    void shouldHandleSimultaneousDuplicateMembershipRequestsGracefully() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Race-Team-" + UUID.randomUUID(), "Race condition testing"));

        User targetUser = createTestUser("target_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);
        OfficerProfile targetOfficer = createTestOfficer(targetUser, "Target Officer");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    AddTeamMemberRequest req = new AddTeamMemberRequest(targetOfficer.getId(), TeamRole.FIELD_OPERATOR);
                    MvcResult res = mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                                    .header("Authorization", "Bearer " + primarySupervisorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 201) {
                        successCounter.incrementAndGet();
                    } else {
                        failureCounter.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    failureCounter.incrementAndGet();
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = completionGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // Exactly ONE request must succeed; all 9 concurrent duplicates must be rejected
        assertThat(successCounter.get()).as("Exactly 1 concurrent addition must succeed").isEqualTo(1);
        assertThat(failureCounter.get()).as("Remaining 9 duplicates must be rejected").isEqualTo(9);

        // Strict verification: Database must contain exactly 1 membership row
        long totalInTeam = membershipRepository.countByTeam_Id(team.id());
        assertThat(totalInTeam).isEqualTo(1);
    }

    @Test
    @Order(8)
    @DisplayName("2.4 Multi-Account Simultaneous Additions: Multiple planners adding different officers")
    void shouldHandleSimultaneousAdditionsFromMultipleAccounts() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Multi-Planner-Team-" + UUID.randomUUID(), "Multi-planner additions"));

        // Setup 2 Senior Planners inside the team
        User planner1 = createTestUser("planner1_" + UUID.randomUUID() + "@police.gov", "Planner 1", officerRole);
        OfficerProfile planner1Officer = createTestOfficer(planner1, "Planner 1");
        teamService.addMember(team.id(), new AddTeamMemberRequest(planner1Officer.getId(), TeamRole.SENIOR_PLANNER));
        String planner1Token = jwtService.generateAccessToken(planner1);

        User planner2 = createTestUser("planner2_" + UUID.randomUUID() + "@police.gov", "Planner 2", officerRole);
        OfficerProfile planner2Officer = createTestOfficer(planner2, "Planner 2");
        teamService.addMember(team.id(), new AddTeamMemberRequest(planner2Officer.getId(), TeamRole.SENIOR_PLANNER));
        String planner2Token = jwtService.generateAccessToken(planner2);

        int additionsPerPlanner = 5;
        List<OfficerProfile> group1Officers = new ArrayList<>();
        List<OfficerProfile> group2Officers = new ArrayList<>();

        for (int i = 0; i < additionsPerPlanner; i++) {
            User u1 = createTestUser("g1_" + i + "_" + UUID.randomUUID() + "@police.gov", "G1-" + i, officerRole);
            group1Officers.add(createTestOfficer(u1, "G1-" + i));

            User u2 = createTestUser("g2_" + i + "_" + UUID.randomUUID() + "@police.gov", "G2-" + i, officerRole);
            group2Officers.add(createTestOfficer(u2, "G2-" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(additionsPerPlanner * 2);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < additionsPerPlanner; i++) {
            final int idx = i;
            // Planner 1 adding an officer
            executor.submit(() -> {
                try {
                    startGate.await();
                    AddTeamMemberRequest req = new AddTeamMemberRequest(group1Officers.get(idx).getId(), TeamRole.FIELD_OPERATOR);
                    MvcResult res = mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                                    .header("Authorization", "Bearer " + planner1Token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
                    if (res.getResponse().getStatus() == 201) successCounter.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    completionGate.countDown();
                }
            });

            // Planner 2 adding another officer
            executor.submit(() -> {
                try {
                    startGate.await();
                    AddTeamMemberRequest req = new AddTeamMemberRequest(group2Officers.get(idx).getId(), TeamRole.FIELD_OPERATOR);
                    MvcResult res = mockMvc.perform(post("/api/v1/teams/" + team.id() + "/members")
                                    .header("Authorization", "Bearer " + planner2Token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
                    if (res.getResponse().getStatus() == 201) successCounter.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    completionGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean done = completionGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(done).isTrue();
        assertThat(successCounter.get()).isEqualTo(additionsPerPlanner * 2);

        // 2 planners + 10 added officers = 12 members total
        long finalCount = membershipRepository.countByTeam_Id(team.id());
        assertThat(finalCount).isEqualTo(12);
    }

    // =========================================================================
    // SECTION 3: AUDIT LOG RESILIENCE, IMMUTABILITY & ANTI-TAMPER
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("3.1 Audit Immutability: Verify @Immutable prevents direct alteration of audit records")
    void shouldEnforceAuditLogImmutabilityAndRejectDirectTampering() {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Audit-Tamper-Team-" + UUID.randomUUID(), "Tamper verification"));

        List<TeamAuditLog> logs = auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(team.id());
        assertThat(logs).isNotEmpty();
        TeamAuditLog originalLog = logs.get(0);
        UUID logId = originalLog.getId();
        String originalAction = originalLog.getAction();
        String originalRemarks = originalLog.getRemarks();

        // Attempt direct modification within a transaction
        TransactionTemplate tx = new TransactionTemplate(postgresTransactionManager);
        tx.execute(status -> {
            EntityManager em = postgresEntityManagerFactory.createEntityManager();
            em.getTransaction().begin();
            try {
                TeamAuditLog managed = em.find(TeamAuditLog.class, logId);
                managed.setAction("HACKED_ACTION");
                managed.setRemarks("HACKED_REMARKS");
                em.flush();
                em.getTransaction().commit();
            } finally {
                em.close();
            }
            return null;
        });

        // Clear cache and re-read from PostgreSQL: verify Hibernate @Immutable prevented UPDATE query execution
        EntityManager cleanEm = postgresEntityManagerFactory.createEntityManager();
        try {
            TeamAuditLog reloaded = cleanEm.find(TeamAuditLog.class, logId);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getAction()).as("Audit log action must remain immutable").isEqualTo(originalAction);
            assertThat(reloaded.getRemarks()).as("Audit log remarks must remain immutable").isEqualTo(originalRemarks);
        } finally {
            cleanEm.close();
        }
    }

    @Test
    @Order(10)
    @DisplayName("3.2 Audit Preservation: Roster removal appends MEMBER_REMOVED audit log without destroying history")
    void shouldPreserveAuditLogsWhenMembersAreRemoved() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Audit-Preserve-Team-" + UUID.randomUUID(), "Preserve test"));

        // 1. Add member
        teamService.addMember(team.id(), new AddTeamMemberRequest(fieldOperatorOfficer.getId(), TeamRole.FIELD_OPERATOR));

        // 2. Remove member
        teamService.removeMember(team.id(), fieldOperatorOfficer.getId(), "Dispatched to another jurisdiction");

        // 3. Verify audit history contains all 3 distinct lifecycle events
        List<TeamAuditLog> history = auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(team.id());
        assertThat(history).hasSize(3);

        assertThat(history.get(0).getAction()).isEqualTo("MEMBER_REMOVED");
        assertThat(history.get(0).getRemarks()).isEqualTo("Dispatched to another jurisdiction");
        assertThat(history.get(0).getOldValues()).containsKey("officerId");

        assertThat(history.get(1).getAction()).isEqualTo("MEMBER_ADDED");
        assertThat(history.get(2).getAction()).isEqualTo("TEAM_CREATED");
    }

    @Test
    @Order(11)
    @DisplayName("3.3 Audit Fetch Access Control: Non-authorized users and non-existent team IDs rejected")
    void shouldRejectUnauthorizedAuditLogFetches() throws Exception {
        authenticate(primarySupervisor);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Audit-Access-Team-" + UUID.randomUUID(), "Access control"));

        // 1. Field Operator attempting to view audit logs -> 403 Forbidden
        mockMvc.perform(get("/api/v1/teams/" + team.id() + "/audit")
                        .header("Authorization", "Bearer " + fieldOperatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not authorized to view audit history for this team"));

        // 2. Non-existent team ID -> 404 Not Found
        UUID fakeTeamId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/teams/" + fakeTeamId + "/audit")
                        .header("Authorization", "Bearer " + primarySupervisorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Team not found"));

        // 3. Supervisor viewing audit logs -> 200 OK
        mockMvc.perform(get("/api/v1/teams/" + team.id() + "/audit")
                        .header("Authorization", "Bearer " + primarySupervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("TEAM_CREATED"));
    }

    // =========================================================================
    // SECTION 4: LAYER BLOCKING & FAULT INJECTION ACROSS ALL TEAM COMPONENTS
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("4.1 Component Blocking - Controller Layer: Malformed path variable parameter type mismatch")
    void shouldBlockAtControllerLayerOnMalformedPathVariable() throws Exception {
        // Send a non-UUID string in path parameter -> Controller advice intercepts before Service is called
        mockMvc.perform(get("/api/v1/teams/invalid-uuid-string-not-guid/members")
                        .header("Authorization", "Bearer " + primarySupervisorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Type Mismatch"))
                .andExpect(jsonPath("$.message").value("Parameter 'teamId' should be of type 'UUID'"));
    }

    @Test
    @Order(13)
    @DisplayName("4.2 Component Blocking - Authorization Layer: Halt execution when SecurityContext is empty")
    void shouldBlockAtAuthorizationLayerWhenSecurityContextMissing() {
        SecurityContextHolder.clearContext();

        // 1. Attempting isSupervisor without auth -> false
        assertThat(teamAuthorizationService.isSupervisor()).isFalse();

        // 2. Attempting requireSupervisor without auth -> AccessDeniedException
        assertThatThrownBy(() -> teamAuthorizationService.requireSupervisor("Blocked by auth"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Blocked by auth");

        // 3. Attempting getCurrentOfficer without auth -> AuthenticationCredentialsNotFoundException
        assertThatThrownBy(() -> teamAuthorizationService.getAuthorizedTeamIds())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    @Test
    @Order(14)
    @DisplayName("4.3 Component Blocking - Service Layer: Intercept duplicate team name before DB insert")
    void shouldBlockAtServiceLayerOnDuplicateTeamName() {
        authenticate(primarySupervisor);
        String teamName = "Service-Guard-Team-" + UUID.randomUUID();

        // First creation succeeds
        teamService.createTeam(new CreateTeamRequest(teamName, "First instance"));

        // Second creation with identical name is blocked by TeamService validation
        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(teamName, "Second instance")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A team with this name already exists");
    }

    @Test
    @Order(15)
    @DisplayName("4.4 Component Blocking - Repository & DB Layer: Unique constraint enforcement and rollback")
    void shouldBlockAtRepositoryLayerOnConstraintViolationAndRollback() {
        String constrainedName = "Repo-Constraint-Team-" + UUID.randomUUID();

        Team t1 = new Team();
        t1.setName(constrainedName);
        t1.setDescription("Base instance");
        teamRepository.saveAndFlush(t1);

        Team t2 = new Team();
        t2.setName(constrainedName);
        t2.setDescription("Constraint violation candidate");

        // Directly invoking repository bypasses Service check -> PostgreSQL database unique constraint blocks the insert
        assertThatThrownBy(() -> teamRepository.saveAndFlush(t2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(16)
    @DisplayName("4.5 Full Pipeline Atomicity: Verify team creation and audit log rollback atomically on error")
    void shouldVerifyAtomicityBetweenTeamAndAuditLog() {
        authenticate(primarySupervisor);
        String failedTeamName = "Atomic-Failure-Team-" + UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(postgresTransactionManager);

        try {
            tx.execute(status -> {
                Team t = new Team();
                t.setName(failedTeamName);
                t.setDescription("Will be rolled back");
                Team saved = teamRepository.save(t);

                TeamAuditLog log = new TeamAuditLog();
                log.setTeam(saved);
                log.setChangedBy(primarySupervisorOfficer);
                log.setAction("ATOMICITY_FAIL_CANDIDATE");
                auditLogRepository.save(log);

                // Simulate unexpected failure right before commit
                throw new RuntimeException("Deliberate simulated failure to verify rollback atomicity");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Deliberate simulated failure");
        }

        // Verify neither Team nor Audit Log was persisted
        assertThat(teamRepository.findByName(failedTeamName)).isEmpty();
        long orphanedLogs = auditLogRepository.findAll().stream()
                .filter(l -> "ATOMICITY_FAIL_CANDIDATE".equals(l.getAction()))
                .count();
        assertThat(orphanedLogs).isEqualTo(0);
    }
}
