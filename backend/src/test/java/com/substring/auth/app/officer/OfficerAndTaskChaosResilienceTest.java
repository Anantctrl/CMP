package com.substring.auth.app.officer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.officer.dto.*;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.entity.Task;
import com.substring.auth.app.officer.entity.TaskAssignment;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.officer.repository.TaskAssignmentRepository;
import com.substring.auth.app.officer.repository.TaskAuditLogRepository;
import com.substring.auth.app.officer.repository.TaskRepository;
import com.substring.auth.app.officer.service.TaskAssignmentService;
import com.substring.auth.app.officer.service.TaskService;
import com.substring.auth.app.team.dto.AddTeamMemberRequest;
import com.substring.auth.app.team.dto.CreateTeamRequest;
import com.substring.auth.app.team.dto.TeamResponse;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.service.TeamService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Officer & Task Subsystem - Chaos, Destruction & Resilience Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OfficerAndTaskChaosResilienceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskAssignmentService assignmentService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private Role supervisorRole;
    private Role officerRole;

    private User supervisorUser;
    private User plannerUser;
    private User constableUser;
    private User externalUser;

    private OfficerProfile supervisorOfficer;
    private OfficerProfile plannerOfficer;
    private OfficerProfile constableOfficer;
    private OfficerProfile externalOfficer;

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

        supervisorUser = createTestUser("sup_task_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        plannerUser = createTestUser("plan_task_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        constableUser = createTestUser("const_task_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);
        externalUser = createTestUser("ext_task_" + UUID.randomUUID() + "@police.gov", "External Constable", officerRole);

        supervisorOfficer = createTestOfficer(supervisorUser, "Commander");
        plannerOfficer = createTestOfficer(plannerUser, "Senior Planner");
        constableOfficer = createTestOfficer(constableUser, "Constable");
        externalOfficer = createTestOfficer(externalUser, "External Constable");

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
    // SECTION 1: TASK CREATION CONTROLLER & BOUNDARY CHAOS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Controller Access: Unauthenticated task creation rejected with 401")
    void shouldRejectUnauthenticatedTaskCreation() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(null, "Patrol Alpha", "PATROLLING", "Desc", "HQ", 18.52, 73.85);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 RBAC: Constable rejected (403) from creating tasks")
    void shouldRejectConstableFromCreatingTask() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(null, "Unauthorized Patrol", "PATROLLING", "Desc", "HQ", 18.52, 73.85);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Input Validation: Malformed tasks (blank title, invalid action type, lat bounds) return 400")
    void shouldRejectInvalidTaskInputs() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Task-Val-Team-" + UUID.randomUUID(), "Desc"));

        // 1. Blank title
        CreateTaskRequest req1 = new CreateTaskRequest(team.id(), "", "PATROLLING", "Desc", "HQ", 18.52, 73.85);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 2. Invalid action type
        CreateTaskRequest req2 = new CreateTaskRequest(team.id(), "Invalid Action", "NON_EXISTENT_ACTION", "Desc", "HQ", 18.52, 73.85);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid action type"));

        // 3. Mismatched Lat/Long (lat without lon)
        CreateTaskRequest req3 = new CreateTaskRequest(team.id(), "Mismatched Coords", "PATROLLING", "Desc", "HQ", 18.52, null);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Latitude and longitude must be provided together"));

        // 4. Latitude out of bounds (95.0 > 90.0)
        CreateTaskRequest req4 = new CreateTaskRequest(team.id(), "Out of Bounds Lat", "PATROLLING", "Desc", "HQ", 95.0, 73.85);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid latitude"));
    }

    // =========================================================================
    // SECTION 2: TASK ASSIGNMENT & STATE MACHINE DESTRUCTION
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("2.1 Service Assignment: Non-team member assignment rejected with 400")
    void shouldRejectAssigningOfficerFromOutsideTaskTeam() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Assign-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));

        // Create Task under this team
        CreateTaskRequest taskReq = new CreateTaskRequest(team.id(), "Patrol Area 1", "PATROLLING", "Patrol description", "Gate 1", 18.52, 73.85);
        Task task = taskService.createTask(taskReq);

        // Attempt to assign external officer who is NOT in this team
        AssignOfficerRequest assignReq = new AssignOfficerRequest(externalOfficer.getId(), "Perimeter Guard", "Monitor checkpoint");

        mockMvc.perform(post("/api/v1/assignments/tasks/" + task.getId() + "/assign")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Target officer does not belong to the task's team"));
    }

    @Test
    @Order(5)
    @DisplayName("2.2 Duplicate Assignment: Assigning same officer twice to the same task rejected with 400")
    void shouldRejectDuplicateOfficerAssignmentToSameTask() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Dup-Assign-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        Task task = taskService.createTask(new CreateTaskRequest(team.id(), "Nakabandi Point B", "NAKABANDI", "Vehicle check", "Junction", 18.52, 73.85));

        AssignOfficerRequest assignReq = new AssignOfficerRequest(constableOfficer.getId(), "Vehicle Inspector", "Check boot & trunk");

        // 1. First assignment succeeds -> 201 Created
        mockMvc.perform(post("/api/v1/assignments/tasks/" + task.getId() + "/assign")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // 2. Duplicate assignment fails -> 400 Bad Request
        mockMvc.perform(post("/api/v1/assignments/tasks/" + task.getId() + "/assign")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This officer is already assigned to this task"));
    }

    @Test
    @Order(6)
    @DisplayName("2.3 State Machine Breakdown: Out-of-order lifecycle transitions strictly rejected")
    void shouldRejectInvalidAssignmentStateTransitions() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Lifecycle-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        Task task = taskService.createTask(new CreateTaskRequest(team.id(), "Bandobast Sector 3", "BANDOBAST", "Crowd control", "Square", 18.52, 73.85));
        TaskAssignment assignment = assignmentService.assignOfficer(task.getId(), constableOfficer.getId(), "Crowd Watch", "Stand at gate");

        // Failure 1: Cannot submit assignment without starting it
        SubmitAssignmentRequest submitReq = new SubmitAssignmentRequest("All peaceful, report submitted");
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/submit")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only in-progress assignments can be submitted"));

        // Failure 2: Cannot approve assignment before submission
        ReviewAssignmentRequest reviewReq = new ReviewAssignmentRequest("Premature approval");
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/approve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only submitted assignments can be approved"));

        // Step A: Correct transition -> Constable starts assignment
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/start")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Failure 3: Cannot start an already started assignment
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/start")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only assigned tasks can be started"));

        // Step B: Correct transition -> Constable submits assignment
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/submit")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Failure 4: Constable attempts to self-approve completion -> 403 Forbidden
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/approve")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Step C: Supervisor approves completion -> 200 OK & Status COMPLETED
        mockMvc.perform(post("/api/v1/assignments/" + assignment.getId() + "/approve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // =========================================================================
    // SECTION 3: AUDIT LOG ATOMICITY & IMMUTABILITY
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("3.1 Audit Integrity: Task lifecycle mutations produce immutable audit log entries")
    void shouldPersistTaskLifecycleAuditTrail() {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Audit-Task-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        Task task = taskService.createTask(new CreateTaskRequest(team.id(), "Investigation Task", "INVESTIGATION", "Case file 402", "Station", 18.52, 73.85));
        TaskAssignment assignment = assignmentService.assignOfficer(task.getId(), constableOfficer.getId(), "Evidence Collection", "Process scene");

        // Verify audit log repository captured assignment
        var auditLogs = taskAuditLogRepository.findAll();
        boolean hasAssignmentAudit = auditLogs.stream()
                .anyMatch(log -> log.getAction().equals("OFFICER_ASSIGNED") &&
                        log.getTask().getId().equals(task.getId()));

        assertThat(hasAssignmentAudit)
                .as("Task assignment action must create an immutable audit log record")
                .isTrue();
    }

    // =========================================================================
    // SECTION 4: CONCURRENCY, SERVER PRESSURE & RACE CONDITIONS
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("4.1 Concurrency: Simultaneous duplicate assignments of same officer handled cleanly (1 win, rest rejected)")
    void shouldPreventConcurrentDuplicateAssignmentsUnderRaceConditions() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Race-Assign-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        Task task = taskService.createTask(new CreateTaskRequest(team.id(), "Race Nakabandi", "NAKABANDI", "Vehicle check", "Junction", 18.52, 73.85));

        int concurrentRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(concurrentRequests);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger duplicateRejectCounter = new AtomicInteger(0);

        AssignOfficerRequest assignReq = new AssignOfficerRequest(constableOfficer.getId(), "Inspector", "Check docs");
        String payload = objectMapper.writeValueAsString(assignReq);

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/assignments/tasks/" + task.getId() + "/assign")
                                    .header("Authorization", "Bearer " + supervisorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 201) {
                        successCounter.incrementAndGet();
                    } else if (status >= 400) {
                        duplicateRejectCounter.incrementAndGet();
                    }
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
        assertThat(successCounter.get())
                .as("Exactly one concurrent assignment should succeed")
                .isEqualTo(1);
        assertThat(duplicateRejectCounter.get())
                .as("All competing duplicate requests must be rejected")
                .isEqualTo(concurrentRequests - 1);
    }

    @Test
    @Order(9)
    @DisplayName("4.2 Burst Pressure: 20 simultaneous task creations under high load")
    void shouldHandleConcurrentTaskCreationsUnderServerPressure() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Burst-Task-Team-" + UUID.randomUUID(), "Desc"));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(threadCount);
        AtomicInteger createdCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreateTaskRequest req = new CreateTaskRequest(
                            team.id(),
                            "Concurrent Task " + idx,
                            "GENERAL_DUTY",
                            "High load test description",
                            "Sector " + idx,
                            18.52,
                            73.85
                    );
                    MvcResult res = mockMvc.perform(post("/api/v1/tasks")
                                    .header("Authorization", "Bearer " + supervisorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 201) {
                        createdCount.incrementAndGet();
                    }
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
        assertThat(createdCount.get()).isEqualTo(threadCount);
    }
}
