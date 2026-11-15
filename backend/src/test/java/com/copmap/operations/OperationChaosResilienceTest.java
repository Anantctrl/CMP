package com.copmap.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.auth.services.impl.JwtService;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.repository.OfficerProfileRepository;
import com.copmap.operations.common.dto.*;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationAssignment;
import com.copmap.operations.common.enums.OperationAssignmentStatus;
import com.copmap.operations.common.enums.OperationStatus;
import com.copmap.operations.common.enums.OperationType;
import com.copmap.operations.common.repository.OperationAssignmentRepository;
import com.copmap.operations.common.repository.OperationRepository;
import com.copmap.operations.common.service.OperationApprovalService;
import com.copmap.operations.common.service.OperationAssignmentService;
import com.copmap.operations.common.service.OperationLocationService;
import com.copmap.operations.common.service.OperationService;
import com.copmap.team.dto.AddTeamMemberRequest;
import com.copmap.team.dto.CreateTeamRequest;
import com.copmap.team.dto.TeamResponse;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.service.TeamService;
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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Operations Subsystem - Tactical Operations, Assignments & State Chaos Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OperationChaosResilienceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OperationService operationService;

    @Autowired
    private OperationLocationService locationService;

    @Autowired
    private OperationAssignmentService assignmentService;

    @Autowired
    private OperationApprovalService approvalService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationAssignmentRepository assignmentRepository;

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

        supervisorUser = createTestUser("sup_ops_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        plannerUser = createTestUser("plan_ops_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        constableUser = createTestUser("const_ops_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

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
    // SECTION 1: OPERATION CREATION & RBAC DESTRUCTION
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Controller Access: Unauthenticated operation creation returns 401")
    void shouldRejectUnauthenticatedOperationCreation() throws Exception {
        CreateOperationRequest req = new CreateOperationRequest(
                OperationType.BANDOBAST,
                "VIP Route Convoy",
                "Securing dignitary convoy route",
                null,
                "Urgent"
        );

        mockMvc.perform(post("/api/v1/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 RBAC: Constable rejected (403) from creating operations")
    void shouldRejectConstableFromCreatingOperation() throws Exception {
        CreateOperationRequest req = new CreateOperationRequest(
                OperationType.NAKABANDI,
                "Illegal Raid Operation",
                "Constable attempting unauthorized operation creation",
                null,
                "High priority"
        );

        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only supervisors or senior planners can create operations"));
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Input Validation: Blank title, null type, or oversized title (>150 chars) returns 400")
    void shouldRejectInvalidOperationCreationPayloads() throws Exception {
        // 1. Missing operation type
        String nullTypeJson = "{\"title\":\"Operation Alpha\",\"description\":\"Desc\"}";
        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 2. Blank title
        CreateOperationRequest blankTitleReq = new CreateOperationRequest(
                OperationType.PATROLLING,
                "",
                "Description",
                null,
                "Remarks"
        );
        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankTitleReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 3. Oversized title > 150 chars
        CreateOperationRequest oversizedReq = new CreateOperationRequest(
                OperationType.PATROLLING,
                "A".repeat(151),
                "Description",
                null,
                "Remarks"
        );
        mockMvc.perform(post("/api/v1/operations")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // SECTION 2: OPERATION STATE MACHINE BREAKDOWN
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("2.1 Invalid Status Transition: DRAFT directly to COMPLETED returns 409 Conflict")
    void shouldRejectIllegalStatusTransitionFromDraftToCompleted() throws Exception {
        authenticate(supervisorUser);
        Operation op = operationService.createOperation(
                OperationType.BANDOBAST,
                "Marathon Security Event",
                "Traffic and perimeter management",
                null,
                "Scheduled for weekend"
        );
        assertThat(op.getStatus()).isEqualTo(OperationStatus.DRAFT);

        ChangeOperationStatusRequest req = new ChangeOperationStatusRequest(
                OperationStatus.COMPLETED,
                "Premature closure attempt"
        );

        mockMvc.perform(patch("/api/v1/operations/" + op.getId() + "/status")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Invalid operation status transition: DRAFT -> COMPLETED"));
    }

    @Test
    @Order(5)
    @DisplayName("2.2 Active Assignment Constraint: Cannot close operation while assignments are active (409 Conflict)")
    void shouldRejectClosingOperationWithActiveAssignments() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Op-Active-Team-" + UUID.randomUUID(), "Desc"));
        teamService.addMember(team.id(), new AddTeamMemberRequest(constableOfficer.getId(), TeamRole.FIELD_OPERATOR));

        // Create operation
        Operation op = operationService.createOperation(
                OperationType.NAKABANDI,
                "Checkpoint Operation 5",
                "High-alert vehicle screening",
                Set.of(team.id()),
                "Night shift"
        );

        // Create location
        CreateOperationLocationRequest locReq = new CreateOperationLocationRequest(
                "Toll Plaza Alpha",
                "Main entry checkpoint",
                "Expressway km 14",
                18.5204,
                73.8567,
                2,
                1
        );
        OperationLocationResponse location = locationService.createLocation(op.getId(), locReq);

        // Schedule and activate operation: DRAFT -> SCHEDULED -> ACTIVE
        operationService.scheduleOperation(
                op.getId(),
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(4),
                "Scheduled on time"
        );
        operationService.changeOperationStatus(op.getId(), OperationStatus.ACTIVE, "Commencing operation");

        // Assign officer to location (status = ASSIGNED)
        OperationAssignment assignment = assignmentService.assignOfficer(
                location.id(),
                constableOfficer.getId(),
                "Vehicle Search",
                "Check passenger IDs"
        );
        assertThat(assignment.getStatus()).isEqualTo(OperationAssignmentStatus.ASSIGNED);

        // Attempt to close operation while assignment is still ASSIGNED -> 409 Conflict
        ChangeOperationStatusRequest closeReq = new ChangeOperationStatusRequest(
                OperationStatus.COMPLETED,
                "Attempting to complete with active officers"
        );

        mockMvc.perform(patch("/api/v1/operations/" + op.getId() + "/status")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("All officer assignments must be completed or cancelled before closing the operation"));
    }

    // =========================================================================
    // SECTION 3: CROSS-TEAM APPROVAL WORKFLOW CHAOS
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("3.1 Cross-Team Approval: Multi-team operations require supervisor approval")
    void shouldTriggerCrossTeamApprovalWorkflow() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team1 = teamService.createTeam(new CreateTeamRequest("Cross-Team-1-" + UUID.randomUUID(), "Desc 1"));
        TeamResponse team2 = teamService.createTeam(new CreateTeamRequest("Cross-Team-2-" + UUID.randomUUID(), "Desc 2"));

        // Senior Planner belongs to team 1
        teamService.addMember(team1.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));

        authenticate(plannerUser);

        // Senior Planner creates cross-team operation involving team1 and team2
        Operation multiTeamOp = operationService.createOperation(
                OperationType.BANDOBAST,
                "Joint Area Combing",
                "Multi-sector sweep",
                Set.of(team1.id(), team2.id()),
                "Requires both unit teams"
        );

        // Verify pending cross-team approval exists
        assertThat(operationService.hasPendingApproval(multiTeamOp.getId())).isTrue();

        // Switch to supervisor and fetch approval requests
        authenticate(supervisorUser);
        var pendingApprovals = approvalService.getApprovalRequests("PENDING");
        Optional<OperationApprovalResponse> targetReq = pendingApprovals.stream()
                .filter(a -> a.operationId().equals(multiTeamOp.getId()))
                .findFirst();

        assertThat(targetReq).isPresent();

        // Supervisor approves request
        ReviewApprovalRequest approveReq = new ReviewApprovalRequest("Approved by Division Commander");
        mockMvc.perform(post("/api/v1/operations/approval-requests/" + targetReq.get().id() + "/approve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // =========================================================================
    // SECTION 4: CONCURRENCY & BURST PRESSURE
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("4.1 Server Pressure: 15 concurrent operation creation requests handled cleanly")
    void shouldHandleConcurrentOperationCreationBurst() throws Exception {
        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreateOperationRequest req = new CreateOperationRequest(
                            OperationType.PATROLLING,
                            "Concurrent Op " + idx + " - " + UUID.randomUUID(),
                            "High concurrency description",
                            null,
                            "Routine patrol"
                    );
                    MvcResult res = mockMvc.perform(post("/api/v1/operations")
                                    .header("Authorization", "Bearer " + supervisorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
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
        assertThat(successCount.get()).isEqualTo(threadCount);
    }
}
