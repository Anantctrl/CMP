package com.copmap.team;

import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.officer.dto.AssignOfficerRequest;
import com.copmap.officer.dto.CreateTaskRequest;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.entity.Task;
import com.copmap.officer.entity.TaskAssignment;
import com.copmap.officer.repository.OfficerProfileRepository;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.officer.service.TaskAssignmentService;
import com.copmap.officer.service.TaskService;
import com.copmap.operations.common.dto.CreateOperationRequest;
import com.copmap.operations.common.entity.Operation;
import com.copmap.operations.common.entity.OperationApprovalRequest;
import com.copmap.operations.common.enums.OperationType;
import com.copmap.operations.common.repository.OperationApprovalRequestRepository;
import com.copmap.operations.common.service.OperationApprovalService;
import com.copmap.operations.common.service.OperationService;
import com.copmap.team.dto.AddTeamMemberRequest;
import com.copmap.team.dto.CreateTeamRequest;
import com.copmap.team.dto.TeamResponse;
import com.copmap.team.enums.TeamRole;
import com.copmap.team.repository.TeamAuditLogRepository;
import com.copmap.team.repository.TeamMembershipRepository;
import com.copmap.team.service.TeamService;
import com.copmap.tracking.dto.LocationUpdateRequest;
import com.copmap.tracking.dto.OfficerLiveLocationResponse;
import com.copmap.tracking.service.LocationTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TeamAndOperationWorkflowIntegrationTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private com.copmap.officer.controller.TaskController taskController;

    @Autowired
    private TaskAssignmentService assignmentService;

    @Autowired
    private OperationService operationService;

    @Autowired
    private OperationApprovalService approvalService;

    @Autowired
    private LocationTrackingService trackingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private TeamMembershipRepository membershipRepository;

    @Autowired
    private TeamAuditLogRepository teamAuditLogRepository;

    @Autowired
    private OperationApprovalRequestRepository approvalRequestRepository;

    private User supervisorUser;
    private User plannerUser;
    private User leadUser;
    private User controlUser;
    private User fieldUser;

    private OfficerProfile supervisorOfficer;
    private OfficerProfile plannerOfficer;
    private OfficerProfile leadOfficer;
    private OfficerProfile controlOfficer;
    private OfficerProfile fieldOfficer;

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

        supervisorUser = createUser("sup_" + UUID.randomUUID() + "@police.gov", "DGP", supervisorRole);
        plannerUser = createUser("plan_" + UUID.randomUUID() + "@police.gov", "Senior Inspector", officerRole);
        leadUser = createUser("lead_" + UUID.randomUUID() + "@police.gov", "Inspector", officerRole);
        controlUser = createUser("ctrl_" + UUID.randomUUID() + "@police.gov", "Sub Inspector", officerRole);
        fieldUser = createUser("field_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);

        supervisorOfficer = createOfficer(supervisorUser, "DGP");
        plannerOfficer = createOfficer(plannerUser, "Senior Inspector");
        leadOfficer = createOfficer(leadUser, "Inspector");
        controlOfficer = createOfficer(controlUser, "Sub Inspector");
        fieldOfficer = createOfficer(fieldUser, "Constable");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String email, String post, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPolicePost(post);
        u.setName("Test " + post);
        u.setPassword("hashedpassword");
        u.setRole(role);
        return userRepository.save(u);
    }

    private OfficerProfile createOfficer(User user, String designation) {
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

    @Test
    void testFullMultiTeamOperationAndTrackingWorkflow() {
        // 1. Supervisor creates Team Alpha and Team Bravo
        authenticate(supervisorUser);

        String alphaName = "Team Alpha " + UUID.randomUUID().toString().substring(0, 8);
        String bravoName = "Team Bravo " + UUID.randomUUID().toString().substring(0, 8);

        TeamResponse teamAlpha = teamService.createTeam(new CreateTeamRequest(alphaName, "Rapid Response"));
        TeamResponse teamBravo = teamService.createTeam(new CreateTeamRequest(bravoName, "Traffic Perimeter"));

        assertNotNull(teamAlpha.id());
        assertNotNull(teamBravo.id());

        // 2. Supervisor assigns roles in Team Alpha
        teamService.addMember(teamAlpha.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));
        teamService.addMember(teamAlpha.id(), new AddTeamMemberRequest(leadOfficer.getId(), TeamRole.TEAM_LEAD));
        teamService.addMember(teamAlpha.id(), new AddTeamMemberRequest(controlOfficer.getId(), TeamRole.CONTROL_OPERATOR));
        teamService.addMember(teamAlpha.id(), new AddTeamMemberRequest(fieldOfficer.getId(), TeamRole.FIELD_OPERATOR));

        // Verify members count
        assertEquals(4, teamService.getTeamMembers(teamAlpha.id()).size());

        // 3. Task Creation: Senior Planner can create a Task in Team Alpha
        authenticate(plannerUser);
        CreateTaskRequest taskReq = new CreateTaskRequest(
                teamAlpha.id(),
                "VVIP Convoy Patrol",
                "PATROLLING",
                "Patrol the eastern highway sector",
                "Eastern Highway",
                18.5204,
                73.8567
        );
        Task createdTask = taskService.createTask(taskReq);
        assertNotNull(createdTask.getId());
        assertEquals("DRAFT", createdTask.getStatus());
        assertEquals(teamAlpha.id(), createdTask.getTeam().getId());

        // 4. Team Lead attempts to create a Task -> MUST FAIL with AccessDeniedException
        authenticate(leadUser);
        CreateTaskRequest invalidTaskReq = new CreateTaskRequest(
                teamAlpha.id(),
                "Unauthorized Task",
                "PATROLLING",
                "Should fail",
                "Gate 1",
                null,
                null
        );
        assertThrows(AccessDeniedException.class, () -> taskService.createTask(invalidTaskReq));

        // 5. Team Lead assigns Officer 4 (Field Operator) to the Task
        TaskAssignment assignment = assignmentService.assignOfficer(
                createdTask.getId(),
                fieldOfficer.getId(),
                "Perimeter Scout",
                "Maintain continuous observation"
        );
        assertNotNull(assignment.getId());
        assertEquals("ASSIGNED", assignment.getStatus());

        // Verify taskController.getTasks(teamAlpha.id()) succeeds without 500 lazy initialization error
        org.springframework.http.ResponseEntity<List<com.copmap.officer.dto.TaskResponse>> tasksResp =
                taskController.getTasks(teamAlpha.id());
        assertEquals(org.springframework.http.HttpStatus.OK, tasksResp.getStatusCode());
        assertNotNull(tasksResp.getBody());
        assertFalse(tasksResp.getBody().isEmpty());
        assertTrue(tasksResp.getBody().get(0).assignedOfficerNames().contains("Test Constable"));

        // 6. Field Operator acknowledges and starts their own assignment
        authenticate(fieldUser);
        TaskAssignment started = assignmentService.startAssignment(assignment.getId());
        assertEquals("IN_PROGRESS", started.getStatus());
        assertNotNull(started.getStartedAt());

        // Field Operator submits completion report
        TaskAssignment submitted = assignmentService.submitAssignment(
                assignment.getId(),
                "Perimeter secured without incident. No anomalies detected."
        );
        assertEquals("SUBMITTED", submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());

        // 7. Senior Planner approves the assignment
        authenticate(plannerUser);
        TaskAssignment approved = assignmentService.approveCompletion(assignment.getId(), "Well executed.");
        assertEquals("COMPLETED", approved.getStatus());

        // 8. Senior Planner creates a cross-team Operation involving Team Alpha and Team Bravo
        // Since Planner is only a Senior Planner in Team Alpha, Team Bravo requires Supervisor approval!
        CreateOperationRequest opReq = new CreateOperationRequest(
                OperationType.BANDOBAST,
                "State Summit Security " + UUID.randomUUID().toString().substring(0, 6),
                "Citywide high-security bandobast",
                Set.of(teamAlpha.id(), teamBravo.id()),
                "Need traffic perimeter support from Team Bravo"
        );
        Operation operation = operationService.createOperation(
                opReq.type(),
                opReq.title(),
                opReq.description(),
                opReq.teamIds(),
                opReq.requestRemarks()
        );
        assertNotNull(operation.getId());
        assertEquals(com.copmap.operations.common.enums.OperationStatus.DRAFT, operation.getStatus());

        // Verify an OperationApprovalRequest was created for Team Bravo
        List<OperationApprovalRequest> pendingRequests = approvalRequestRepository.findAllByOperation_Id(operation.getId());
        assertEquals(1, pendingRequests.size());
        assertEquals("PENDING", pendingRequests.get(0).getStatus());
        assertEquals(teamBravo.id(), pendingRequests.get(0).getTargetTeam().getId());

        // 9. Planner attempts to schedule before approval -> MUST FAIL
        assertThrows(IllegalStateException.class, () -> operationService.scheduleOperation(
                operation.getId(),
                OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now().plusHours(10),
                "Cannot schedule yet"
        ));

        // 10. Supervisor reviews and approves the cross-team request
        authenticate(supervisorUser);
        approvalService.approveRequest(pendingRequests.get(0).getId(), "Approved for summit deployment.");

        // Now operation can be scheduled
        Operation scheduled = operationService.scheduleOperation(
                operation.getId(),
                OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now().plusHours(10),
                "Scheduled after team confirmation"
        );
        assertEquals(com.copmap.operations.common.enums.OperationStatus.PLANNED, scheduled.getStatus());

        // 11. Live GPS Telemetry: Field Operator posts GPS update
        authenticate(fieldUser);
        LocationUpdateRequest locReq = new LocationUpdateRequest(
                18.5204,
                73.8567,
                4.5,
                OffsetDateTime.now()
        );
        OfficerLiveLocationResponse locRes = trackingService.recordLocation(locReq);
        assertNotNull(locRes);
        assertEquals(fieldOfficer.getId(), locRes.officerId());
        assertEquals("FRESH", locRes.freshnessStatus());

        // 12. Control Operator views live map for Team Alpha
        authenticate(controlUser);
        List<OfficerLiveLocationResponse> liveMap = trackingService.getLiveLocationsForTeam(teamAlpha.id());
        assertFalse(liveMap.isEmpty());
        assertTrue(liveMap.stream().anyMatch(l -> l.officerId().equals(fieldOfficer.getId())));

        // 13. Hard Delete Team Member with Audit Preservation
        authenticate(supervisorUser);
        int initialAuditCount = teamAuditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(teamAlpha.id()).size();
        teamService.removeMember(teamAlpha.id(), fieldOfficer.getId(), "Transferred to intelligence division");

        // Verify member is hard deleted from team_memberships
        assertFalse(membershipRepository.existsByTeam_IdAndOfficer_Id(teamAlpha.id(), fieldOfficer.getId()));

        // Verify audit log has the saved record with old values
        int finalAuditCount = teamAuditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(teamAlpha.id()).size();
        assertEquals(initialAuditCount + 1, finalAuditCount);
    }
}
