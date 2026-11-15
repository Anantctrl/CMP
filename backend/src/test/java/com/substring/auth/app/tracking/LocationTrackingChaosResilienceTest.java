package com.substring.auth.app.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import com.substring.auth.app.team.dto.AddTeamMemberRequest;
import com.substring.auth.app.team.dto.CreateTeamRequest;
import com.substring.auth.app.team.dto.TeamResponse;
import com.substring.auth.app.team.enums.TeamRole;
import com.substring.auth.app.team.service.TeamService;
import com.substring.auth.app.tracking.dto.LocationUpdateRequest;
import com.substring.auth.app.tracking.dto.OfficerLiveLocationResponse;
import com.substring.auth.app.tracking.repository.OfficerLocationUpdateRepository;
import com.substring.auth.app.tracking.service.LocationTrackingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Real-Time GPS Tracking Subsystem - Chaos, Destruction & Resilience Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LocationTrackingChaosResilienceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationTrackingService trackingService;

    @Autowired
    private OfficerLocationUpdateRepository locationRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Role supervisorRole;
    private Role officerRole;

    private User supervisorUser;
    private User plannerUser;
    private User constableUser;
    private User strangerUser;

    private OfficerProfile supervisorOfficer;
    private OfficerProfile plannerOfficer;
    private OfficerProfile constableOfficer;
    private OfficerProfile strangerOfficer;

    private String supervisorToken;
    private String plannerToken;
    private String constableToken;
    private String strangerToken;

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

        supervisorUser = createTestUser("sup_gps_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        plannerUser = createTestUser("plan_gps_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        constableUser = createTestUser("const_gps_" + UUID.randomUUID() + "@police.gov", "Constable", officerRole);
        strangerUser = createTestUser("stranger_gps_" + UUID.randomUUID() + "@police.gov", "Stranger", officerRole);

        supervisorOfficer = createTestOfficer(supervisorUser, "Commander");
        plannerOfficer = createTestOfficer(plannerUser, "Senior Planner");
        constableOfficer = createTestOfficer(constableUser, "Constable");
        strangerOfficer = createTestOfficer(strangerUser, "Stranger");

        supervisorToken = jwtService.generateAccessToken(supervisorUser);
        plannerToken = jwtService.generateAccessToken(plannerUser);
        constableToken = jwtService.generateAccessToken(constableUser);
        strangerToken = jwtService.generateAccessToken(strangerUser);
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
    // SECTION 1: GPS INGESTION CONTROLLER & BOUNDARY CHAOS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Controller Access: Unauthenticated GPS location update returns 401")
    void shouldRejectUnauthenticatedLocationUpdate() throws Exception {
        LocationUpdateRequest req = new LocationUpdateRequest(18.5204, 73.8567, 10.0, OffsetDateTime.now());

        mockMvc.perform(post("/api/v1/tracking/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Coordinate Boundary Chaos: Invalid latitudes (>90 or <-90) and longitudes (>180 or <-180) return 400")
    void shouldRejectOutOfBoundsGpsCoordinates() throws Exception {
        // 1. Latitude > 90.0
        LocationUpdateRequest req1 = new LocationUpdateRequest(95.0, 73.8567, 5.0, OffsetDateTime.now());
        mockMvc.perform(post("/api/v1/tracking/update")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Latitude must be between -90 and 90"));

        // 2. Latitude < -90.0
        LocationUpdateRequest req2 = new LocationUpdateRequest(-95.0, 73.8567, 5.0, OffsetDateTime.now());
        mockMvc.perform(post("/api/v1/tracking/update")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Latitude must be between -90 and 90"));

        // 3. Longitude > 180.0
        LocationUpdateRequest req3 = new LocationUpdateRequest(18.5204, 185.0, 5.0, OffsetDateTime.now());
        mockMvc.perform(post("/api/v1/tracking/update")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Longitude must be between -180 and 180"));

        // 4. Negative accuracy meters
        LocationUpdateRequest req4 = new LocationUpdateRequest(18.5204, 73.8567, -10.0, OffsetDateTime.now());
        mockMvc.perform(post("/api/v1/tracking/update")
                        .header("Authorization", "Bearer " + constableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Accuracy cannot be negative"));
    }

    // =========================================================================
    // SECTION 2: RBAC & PRIVACY ACCESS CONTROL
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("2.1 RBAC: Unauthorized officer blocked (403) from tracking another officer's location")
    void shouldEnforcePrivacyAndBlockUnauthorizedOfficerLocationAccess() throws Exception {
        // Constable publishes their own location
        authenticate(constableUser);
        trackingService.recordLocation(new LocationUpdateRequest(18.5204, 73.8567, 5.0, OffsetDateTime.now()));

        // Stranger (with no shared monitored team) tries to spy on Constable's live location -> 403 Forbidden
        mockMvc.perform(get("/api/v1/tracking/officers/" + constableOfficer.getId() + "/latest")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not authorized to view this officer's location"));

        // Constable can view their OWN location -> 200 OK
        mockMvc.perform(get("/api/v1/tracking/officers/" + constableOfficer.getId() + "/latest")
                        .header("Authorization", "Bearer " + constableToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(constableOfficer.getId().toString()));

        // Supervisor can view any officer's location -> 200 OK
        mockMvc.perform(get("/api/v1/tracking/officers/" + constableOfficer.getId() + "/latest")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(constableOfficer.getId().toString()));
    }

    @Test
    @Order(4)
    @DisplayName("2.2 Team Map RBAC: Unauthorized officer blocked from viewing team live tracking map")
    void shouldEnforceTeamMapMonitoringRoleAccess() throws Exception {
        authenticate(supervisorUser);
        TeamResponse team = teamService.createTeam(new CreateTeamRequest("Map-RBAC-Team-" + UUID.randomUUID(), "Desc"));

        // Senior Planner is added to the team
        teamService.addMember(team.id(), new AddTeamMemberRequest(plannerOfficer.getId(), TeamRole.SENIOR_PLANNER));

        // Senior Planner querying team live map -> 200 OK
        mockMvc.perform(get("/api/v1/tracking/teams/" + team.id() + "/live")
                        .header("Authorization", "Bearer " + plannerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Stranger querying team live map -> 403 Forbidden
        mockMvc.perform(get("/api/v1/tracking/teams/" + team.id() + "/live")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not authorized to view live locations for this team"));
    }

    // =========================================================================
    // SECTION 3: MULTI-TIER STORAGE & REDIS FALLBACK RESILIENCE
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("3.1 Storage Resilience: GPS persisted in PostgreSQL and cached in Redis with DB fallback")
    void shouldPersistInPostgresAndFallbackWhenRedisCacheEvicted() {
        authenticate(constableUser);

        // 1. Ingest location
        LocationUpdateRequest req = new LocationUpdateRequest(18.5204, 73.8567, 4.0, OffsetDateTime.now());
        OfficerLiveLocationResponse response = trackingService.recordLocation(req);
        assertThat(response.latitude()).isEqualTo(18.5204);

        // Verify Redis holds cached value
        String redisKey = "officer:live:" + constableOfficer.getId();
        assertThat(redisTemplate.opsForValue().get(redisKey)).isNotNull();

        // 2. Simulate Redis cache eviction / flush
        redisTemplate.delete(redisKey);
        assertThat(redisTemplate.opsForValue().get(redisKey)).isNull();

        // 3. Query location: must transparently recover from PostgreSQL and repopulate Redis
        Optional<OfficerLiveLocationResponse> recovered = trackingService.getOfficerLatestLocation(constableOfficer.getId());
        assertThat(recovered).isPresent();
        assertThat(recovered.get().latitude()).isEqualTo(18.5204);
        assertThat(redisTemplate.opsForValue().get(redisKey)).isNotNull();
    }

    // =========================================================================
    // SECTION 4: CONCURRENCY & BURST PRESSURE
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("4.1 Telemetry Burst: 25 simultaneous GPS location updates processed without deadlocks")
    void shouldHandleConcurrentGpsTelemetryBurst() throws Exception {
        int threadCount = 25;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            double lat = 18.5204 + (i * 0.001);
            double lon = 73.8567 + (i * 0.001);
            executor.submit(() -> {
                try {
                    startGate.await();
                    LocationUpdateRequest req = new LocationUpdateRequest(lat, lon, 5.0, OffsetDateTime.now());
                    MvcResult res = mockMvc.perform(post("/api/v1/tracking/update")
                                    .header("Authorization", "Bearer " + constableToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 201) {
                        successCounter.incrementAndGet();
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
        assertThat(successCounter.get()).isEqualTo(threadCount);
    }
}
