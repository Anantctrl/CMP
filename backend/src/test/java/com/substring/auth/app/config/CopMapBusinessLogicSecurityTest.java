package com.substring.auth.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.operations.common.dto.CreateOperationRequest;
import com.substring.auth.app.operations.common.entity.Operation;
import com.substring.auth.app.operations.common.entity.OperationApprovalRequest;
import com.substring.auth.app.operations.common.enums.OperationStatus;
import com.substring.auth.app.operations.common.enums.OperationType;
import com.substring.auth.app.operations.common.repository.OperationApprovalRequestRepository;
import com.substring.auth.app.operations.common.service.OperationApprovalService;
import com.substring.auth.app.operations.common.service.OperationService;
import com.substring.auth.app.team.dto.AddTeamMemberRequest;
import com.substring.auth.app.team.dto.CreateTeamRequest;
import com.substring.auth.app.team.dto.TeamResponse;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.service.TeamService;
import com.substring.auth.app.tracking.dto.LocationUpdateRequest;
import com.substring.auth.app.tracking.dto.OfficerLiveLocationResponse;
import com.substring.auth.app.tracking.service.LocationTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("5. CopMap Business Logic & Tactical Security Test Suite")
public class CopMapBusinessLogicSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamService teamService;

    @Autowired
    private OperationService operationService;

    @Autowired
    private OperationApprovalService approvalService;

    @Autowired
    private OperationApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private LocationTrackingService trackingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    private User supervisorUser;
    private User officerUser;
    private OfficerProfile supervisorProfile;
    private OfficerProfile officerProfile;

    @BeforeEach
    void setUp() {
        Role supervisorRole = roleRepository.findByName("Supervisor")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Supervisor");
                    return roleRepository.save(r);
                });

        Role officerRole = roleRepository.findByName("Not_Assigned")
                .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Not_Assigned");
                    return roleRepository.save(r);
                });

        supervisorUser = new User();
        supervisorUser.setEmail("supervisor." + UUID.randomUUID() + "@copmap.gov");
        supervisorUser.setName("Commander Supervisor");
        supervisorUser.setPassword("Pass1234!");
        supervisorUser.setPolicePost("HQ Central Command");
        supervisorUser.setRole(supervisorRole);
        supervisorUser = userRepository.save(supervisorUser);

        supervisorProfile = new OfficerProfile();
        supervisorProfile.setMysqlUserId(supervisorUser.getId());
        supervisorProfile.setOfficerName(supervisorUser.getName());
        supervisorProfile.setDesignation("Division Commander");
        supervisorProfile = officerRepository.save(supervisorProfile);

        officerUser = new User();
        officerUser.setEmail("officer." + UUID.randomUUID() + "@copmap.gov");
        officerUser.setName("Constable John");
        officerUser.setPassword("Pass1234!");
        officerUser.setPolicePost("North Sector Post");
        officerUser.setRole(officerRole);
        officerUser = userRepository.save(officerUser);

        officerProfile = new OfficerProfile();
        officerProfile.setMysqlUserId(officerUser.getId());
        officerProfile.setOfficerName(officerUser.getName());
        officerProfile.setDesignation("Patrol Officer");
        officerProfile = officerRepository.save(officerProfile);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user, String roleName) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority(roleName)))
        );
    }

    @Nested
    @DisplayName("Authentication & Role Authorization")
    class AuthAndRoleTests {

        @Test
        @DisplayName("Verify Unauthorized Access Without JWT Returns 401")
        void shouldRejectUnauthenticatedRequestWith401() throws Exception {
            mockMvc.perform(get("/api/v1/teams")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Verify Field Officer Attempting to Create Team Throws AccessDeniedException")
        void shouldRejectFieldOfficerCreatingTeam() {
            authenticateAs(officerUser, "Not_Assigned");

            CreateTeamRequest request = new CreateTeamRequest("Unauthorized Team", "Description");
            assertThatThrownBy(() -> teamService.createTeam(request))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Verify Supervisor Access: Can Create and View Teams")
        void shouldAllowSupervisorToCreateAndManageTeams() {
            authenticateAs(supervisorUser, "Supervisor");

            CreateTeamRequest request = new CreateTeamRequest("Tactical Alpha " + UUID.randomUUID(), "Rapid Response");
            TeamResponse team = teamService.createTeam(request);

            assertThat(team).isNotNull();
            assertThat(team.id()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Business Rule Violations: Roster & Approvals")
    class BusinessRuleTests {

        @Test
        @DisplayName("Duplicate Assignment: Re-adding Officer to Same Team Throws IllegalArgumentException")
        void shouldRejectDuplicateOfficerAssignment() {
            authenticateAs(supervisorUser, "Supervisor");

            CreateTeamRequest request = new CreateTeamRequest("Bravo Team " + UUID.randomUUID(), "Perimeter Guard");
            TeamResponse team = teamService.createTeam(request);

            AddTeamMemberRequest addReq = new AddTeamMemberRequest(officerProfile.getId(), TeamRole.FIELD_OPERATOR);
            teamService.addMember(team.id(), addReq);

            // Re-adding same officer must fail with duplicate error
            assertThatThrownBy(() -> teamService.addMember(team.id(), addReq))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Officer is already a member of this team");
        }

        @Test
        @DisplayName("Operation Cross-Team Approval Gate: Only Supervisor Can Approve Pending Requests")
        void shouldEnforceSupervisorApprovalGateOnOperations() {
            authenticateAs(supervisorUser, "Supervisor");

            CreateTeamRequest teamReq1 = new CreateTeamRequest("Ops Team A " + UUID.randomUUID(), "Perimeter");
            CreateTeamRequest teamReq2 = new CreateTeamRequest("Ops Team B " + UUID.randomUUID(), "Support");
            TeamResponse team1 = teamService.createTeam(teamReq1);
            TeamResponse team2 = teamService.createTeam(teamReq2);

            // Supervisor creates operation involving team1 and team2
            CreateOperationRequest opReq = new CreateOperationRequest(
                    OperationType.BANDOBAST,
                    "Operation Red Dawn " + UUID.randomUUID(),
                    "Joint anti-smuggling raid",
                    Set.of(team1.id(), team2.id()),
                    "Need perimeter support"
            );

            Operation op = operationService.createOperation(
                    opReq.type(),
                    opReq.title(),
                    opReq.description(),
                    opReq.teamIds(),
                    opReq.requestRemarks()
            );
            assertThat(op.getStatus()).isEqualTo(OperationStatus.DRAFT);

            // Check pending requests
            List<OperationApprovalRequest> pending = approvalRequestRepository.findAllByOperation_Id(op.getId());
            if (!pending.isEmpty()) {
                UUID reqId = pending.get(0).getId();

                // Field Officer trying to approve must be rejected
                authenticateAs(officerUser, "Not_Assigned");
                assertThatThrownBy(() -> approvalService.approveRequest(reqId, "Unauthorized attempt"))
                        .isInstanceOf(AccessDeniedException.class);

                // Supervisor approves
                authenticateAs(supervisorUser, "Supervisor");
                approvalService.approveRequest(reqId, "Authorized by supervisor");
                OperationApprovalRequest updated = approvalRequestRepository.findById(reqId).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo("APPROVED");
            }
        }
    }

    @Nested
    @DisplayName("Live GPS Telemetry & Freshness Evaluation")
    class TelemetryAndFreshnessTests {

        @Test
        @DisplayName("Officer Submits GPS Telemetry and Signal Evaluates to FRESH")
        void shouldIngestTelemetryAndEvaluateFreshness() {
            authenticateAs(officerUser, "Not_Assigned");

            LocationUpdateRequest updateReq = new LocationUpdateRequest(
                    18.52043,
                    73.85674,
                    4.2,
                    OffsetDateTime.now()
            );

            OfficerLiveLocationResponse response = trackingService.recordLocation(updateReq);
            assertThat(response).isNotNull();
            assertThat(response.officerId()).isEqualTo(officerProfile.getId());
            assertThat(response.freshnessStatus()).isEqualTo("FRESH");
        }

        @Test
        @DisplayName("GPS Accuracy Validation: Negative Accuracy Throws IllegalArgumentException")
        void shouldRejectNegativeGpsAccuracy() {
            authenticateAs(officerUser, "Not_Assigned");

            LocationUpdateRequest invalidReq = new LocationUpdateRequest(
                    18.52043,
                    73.85674,
                    -10.0, // Invalid negative accuracy
                    OffsetDateTime.now()
            );

            assertThatThrownBy(() -> trackingService.recordLocation(invalidReq))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Accuracy cannot be negative");
        }

        @Test
        @DisplayName("GPS Coordinates Validation: Out-of-Range Latitude Throws IllegalArgumentException")
        void shouldRejectOutOfRangeCoordinates() {
            authenticateAs(officerUser, "Not_Assigned");

            LocationUpdateRequest invalidReq = new LocationUpdateRequest(
                    195.0, // Invalid latitude (> 90)
                    73.85674,
                    5.0,
                    OffsetDateTime.now()
            );

            assertThatThrownBy(() -> trackingService.recordLocation(invalidReq))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Latitude must be between -90 and 90");
        }
    }
}
