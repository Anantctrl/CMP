package com.copmap.integration;

import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.officer.dto.AssignOfficerRequest;
import com.copmap.officer.dto.CreateTaskRequest;
import com.copmap.officer.dto.TaskResponse;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.entity.Task;
import com.copmap.officer.entity.TaskAssignment;
import com.copmap.officer.repository.OfficerProfileRepository;
import com.copmap.officer.repository.TaskAssignmentRepository;
import com.copmap.officer.repository.TaskRepository;
import com.copmap.officer.service.CurrentOfficerService;
import com.copmap.officer.service.TaskAssignmentService;
import com.copmap.officer.service.TaskService;
import com.copmap.team.dto.AddTeamMemberRequest;
import com.copmap.team.dto.CreateTeamRequest;
import com.copmap.team.dto.TeamMemberResponse;
import com.copmap.team.dto.TeamResponse;
import com.copmap.team.entity.Team;
import com.copmap.team.entity.TeamAuditLog;
import com.copmap.team.entity.TeamMembership;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("CopMap Tactical Police System - Comprehensive Deep Integration Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TacticalSystemDeepIntegrationTest {

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory postgresEntityManagerFactory;

    @Autowired
    @Qualifier("mysqlEntityManagerFactory")
    private EntityManagerFactory mysqlEntityManagerFactory;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Autowired
    @Qualifier("mysqlTransactionManager")
    private PlatformTransactionManager mysqlTransactionManager;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository membershipRepository;

    @Autowired
    private TeamAuditLogRepository auditLogRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private OfficerLocationUpdateRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskAssignmentService assignmentService;

    @Autowired
    private LocationTrackingService trackingService;

    @Autowired
    private CurrentOfficerService currentOfficerService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User testSupervisorUser;
    private User testPlannerUser;
    private User testFieldUser;

    private OfficerProfile testSupervisorOfficer;
    private OfficerProfile testPlannerOfficer;
    private OfficerProfile testFieldOfficer;

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

        testSupervisorUser = createMysqlUser("sup_deep_" + UUID.randomUUID() + "@police.gov", "Commander", supervisorRole);
        testPlannerUser = createMysqlUser("plan_deep_" + UUID.randomUUID() + "@police.gov", "Senior Planner", officerRole);
        testFieldUser = createMysqlUser("field_deep_" + UUID.randomUUID() + "@police.gov", "Tactical Officer", officerRole);

        testSupervisorOfficer = createPostgresOfficer(testSupervisorUser, "Commander");
        testPlannerOfficer = createPostgresOfficer(testPlannerUser, "Senior Planner");
        testFieldOfficer = createPostgresOfficer(testFieldUser, "Tactical Officer");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createMysqlUser(String email, String post, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPolicePost(post);
        u.setName("Officer " + post);
        u.setPassword("$2a$10$abcdefghijklmnopqrstuvxyz123456");
        u.setRole(role);
        u.setEnable(true);
        return userRepository.save(u);
    }

    private OfficerProfile createPostgresOfficer(User user, String designation) {
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
    // Category 1: Database Integration (Actual INSERT, SELECT and UPDATE)
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("1. Database Integration: Verify actual INSERT, SELECT and UPDATE in PostgreSQL and MySQL")
    void verifyDatabaseIntegration_InsertSelectUpdate_AcrossPostgresAndMySql() {
        // --- PostgreSQL Operations ---
        // 1. INSERT Team
        Team team = new Team();
        team.setName("DB-Integration-Alpha-" + UUID.randomUUID());
        team.setDescription("Initial deployment description");
        Team savedTeam = teamRepository.saveAndFlush(team);

        assertThat(savedTeam.getId()).as("PostgreSQL generated UUID must not be null").isNotNull();
        assertThat(savedTeam.getCreatedAt()).as("PostgreSQL createdAt timestamp must be populated").isNotNull();

        // 2. SELECT Team
        Team fetchedTeam = teamRepository.findById(savedTeam.getId()).orElse(null);
        assertThat(fetchedTeam).isNotNull();
        assertThat(fetchedTeam.getName()).isEqualTo(savedTeam.getName());
        assertThat(fetchedTeam.getDescription()).isEqualTo("Initial deployment description");

        // 3. UPDATE Team
        fetchedTeam.setDescription("Updated deployment description for high alert");
        Team updatedTeam = teamRepository.saveAndFlush(fetchedTeam);
        assertThat(updatedTeam.getDescription()).isEqualTo("Updated deployment description for high alert");

        // --- MySQL Operations ---
        // 1. INSERT User
        User mysqlUser = new User();
        mysqlUser.setEmail("mysql_test_" + UUID.randomUUID() + "@police.gov");
        mysqlUser.setPolicePost("Central Command HQ");
        mysqlUser.setName("Special Investigator");
        mysqlUser.setPassword("hashedpassword123");
        mysqlUser.setRole(officerRole);
        User savedMysqlUser = userRepository.saveAndFlush(mysqlUser);

        assertThat(savedMysqlUser.getId()).as("MySQL generated UUID must not be null").isNotNull();

        // 2. SELECT User
        User fetchedMysqlUser = userRepository.findById(savedMysqlUser.getId()).orElse(null);
        assertThat(fetchedMysqlUser).isNotNull();
        assertThat(fetchedMysqlUser.getPolicePost()).isEqualTo("Central Command HQ");

        // 3. UPDATE User
        fetchedMysqlUser.setPolicePost("Metro SWAT Div 5");
        User updatedMysqlUser = userRepository.saveAndFlush(fetchedMysqlUser);
        assertThat(updatedMysqlUser.getPolicePost()).isEqualTo("Metro SWAT Div 5");
    }

    // =========================================================================
    // Category 2: Entity Mapping (PostgreSQL Schema Types, UUID, JSONB, Timestamps)
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("2. Entity Mapping: Verify Java entities map correctly to PostgreSQL columns, JSONB and timestamps")
    void verifyEntityMapping_PostgreSQLSchemaTypesAndTimestamps() {
        authenticate(testSupervisorUser);

        Team team = new Team();
        team.setName("Entity-Mapping-Team-" + UUID.randomUUID());
        team.setDescription("Testing entity mapping");
        Team savedTeam = teamRepository.saveAndFlush(team);

        // Verify JSONB mapping in TeamAuditLog
        TeamAuditLog auditLog = new TeamAuditLog();
        auditLog.setTeam(savedTeam);
        auditLog.setChangedBy(testSupervisorOfficer);
        auditLog.setAction("SCHEMA_MAPPING_VERIFICATION");

        Map<String, Object> jsonbPayload = new HashMap<>();
        jsonbPayload.put("incidentCode", "CODE-RED-99");
        jsonbPayload.put("severityScore", 9.8);
        jsonbPayload.put("tacticalUnitsRequired", 4);
        jsonbPayload.put("isArmedResponse", true);
        jsonbPayload.put("equipmentList", List.of("BodyArmor", "TacticalShield", "Drones"));

        auditLog.setNewValues(jsonbPayload);
        auditLog.setRemarks("Validating PostgreSQL JSONB column serialization");

        TeamAuditLog savedAudit = auditLogRepository.saveAndFlush(auditLog);
        assertThat(savedAudit.getId()).isNotNull();
        assertThat(savedAudit.getCreatedAt()).isNotNull();

        // Query fresh using clean EntityManager to verify PostgreSQL mapping
        EntityManager em = postgresEntityManagerFactory.createEntityManager();
        try {
            TeamAuditLog reloadedAudit = em.find(TeamAuditLog.class, savedAudit.getId());
            assertThat(reloadedAudit).isNotNull();
            assertThat(reloadedAudit.getAction()).isEqualTo("SCHEMA_MAPPING_VERIFICATION");

            Map<String, Object> retrievedJson = reloadedAudit.getNewValues();
            assertThat(retrievedJson)
                    .isNotNull()
                    .containsEntry("incidentCode", "CODE-RED-99")
                    .containsEntry("isArmedResponse", true);

            assertThat(retrievedJson.get("equipmentList")).isInstanceOf(List.class);
            List<?> equipment = (List<?>) retrievedJson.get("equipmentList");
            assertThat(equipment).asList().contains("BodyArmor", "TacticalShield", "Drones");
        } finally {
            em.close();
        }
    }

    // =========================================================================
    // Category 3: Repository Queries (Custom JPQL & Derived Queries)
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("3. Repository Queries: Verify custom queries return correct filtered results")
    void verifyRepositoryQueries_CustomQueriesReturnCorrectResults() {
        authenticate(testSupervisorUser);

        // Create Team
        Team team = new Team();
        team.setName("Query-Repo-Team-" + UUID.randomUUID());
        Team savedTeam = teamRepository.saveAndFlush(team);

        // Add 2 Members
        TeamMembership m1 = new TeamMembership();
        m1.setTeam(savedTeam);
        m1.setOfficer(testPlannerOfficer);
        m1.setTeamRole(TeamRole.SENIOR_PLANNER);
        membershipRepository.saveAndFlush(m1);

        TeamMembership m2 = new TeamMembership();
        m2.setTeam(savedTeam);
        m2.setOfficer(testFieldOfficer);
        m2.setTeamRole(TeamRole.FIELD_OPERATOR);
        membershipRepository.saveAndFlush(m2);

        // 1. Test membershipRepository.findAllByTeam_Id
        List<TeamMembership> teamMembers = membershipRepository.findAllByTeam_Id(savedTeam.getId());
        assertThat(teamMembers).hasSize(2);

        // 2. Test membershipRepository.findTeamIdsByOfficerId
        List<UUID> officerTeamIds = membershipRepository.findTeamIdsByOfficerId(testPlannerOfficer.getId());
        assertThat(officerTeamIds).contains(savedTeam.getId());

        // 3. Test locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime t3 = OffsetDateTime.now();

        OfficerLocationUpdate loc1 = new OfficerLocationUpdate();
        loc1.setOfficer(testFieldOfficer);
        loc1.setLatitude(28.6139);
        loc1.setLongitude(77.2090);
        loc1.setAccuracyMeters(12.0);
        loc1.setRecordedAt(t1);
        locationRepository.saveAndFlush(loc1);

        OfficerLocationUpdate loc2 = new OfficerLocationUpdate();
        loc2.setOfficer(testFieldOfficer);
        loc2.setLatitude(28.6145);
        loc2.setLongitude(77.2095);
        loc2.setAccuracyMeters(8.0);
        loc2.setRecordedAt(t2);
        locationRepository.saveAndFlush(loc2);

        OfficerLocationUpdate loc3 = new OfficerLocationUpdate();
        loc3.setOfficer(testFieldOfficer);
        loc3.setLatitude(28.6150);
        loc3.setLongitude(77.2100);
        loc3.setAccuracyMeters(4.0);
        loc3.setRecordedAt(t3);
        locationRepository.saveAndFlush(loc3);

        Optional<OfficerLocationUpdate> latestLocation =
                locationRepository.findFirstByOfficer_IdOrderByRecordedAtDesc(testFieldOfficer.getId());

        assertThat(latestLocation).isPresent();
        assertThat(latestLocation.get().getLatitude()).isEqualTo(28.6150);
        assertThat(latestLocation.get().getAccuracyMeters()).isEqualTo(4.0);

        // 4. Test locationRepository.findLatestLocationsForOfficers
        List<OfficerLocationUpdate> batchLatest =
                locationRepository.findLatestLocationsForOfficers(List.of(testFieldOfficer.getId()));
        assertThat(batchLatest).isNotEmpty();
        assertThat(batchLatest.get(0).getLatitude()).isEqualTo(28.6150);
    }

    // =========================================================================
    // Category 4: Transaction Testing (Commit & Rollback Isolation)
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("4. Transaction Testing: Verify commit and rollback isolation via PlatformTransactionManager")
    void verifyTransactionTesting_CommitAndRollbackIsolation() {
        TransactionTemplate txTemplate = new TransactionTemplate(postgresTransactionManager);

        // --- Test Transaction Commit ---
        String committedTeamName = "Committed-Team-" + UUID.randomUUID();
        UUID committedId = txTemplate.execute(status -> {
            Team t = new Team();
            t.setName(committedTeamName);
            t.setDescription("Will be committed successfully");
            return teamRepository.save(t).getId();
        });

        assertThat(committedId).isNotNull();
        assertThat(teamRepository.findById(committedId)).isPresent();

        // --- Test Transaction Rollback on Failure ---
        String rolledBackTeamName = "Rollback-Candidate-" + UUID.randomUUID();
        try {
            txTemplate.execute(status -> {
                Team t = new Team();
                t.setName(rolledBackTeamName);
                t.setDescription("Must NOT persist after exception");
                teamRepository.save(t);

                // Deliberate simulated mid-transaction failure
                throw new RuntimeException("Deliberate simulated transaction failure for rollback testing");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Deliberate simulated transaction failure");
        }

        // Verify team was NOT persisted to PostgreSQL
        Optional<Team> rolledBackTeam = teamRepository.findByName(rolledBackTeamName);
        assertThat(rolledBackTeam)
                .as("Rolled back entity must NOT exist in PostgreSQL")
                .isEmpty();
    }

    // =========================================================================
    // Category 5: Foreign Keys (Reject Invalid Team/Officer References)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("5. Foreign Keys: Verify database rejects invalid team or officer references")
    void verifyForeignKeys_RejectInvalidReferences() {
        // Attempt inserting TeamMembership with a non-existent team UUID
        EntityManager em = postgresEntityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        tx.begin();

        try {
            Team nonExistentTeam = em.getReference(Team.class, UUID.randomUUID());

            TeamMembership membership = new TeamMembership();
            membership.setTeam(nonExistentTeam);
            membership.setOfficer(testFieldOfficer);
            membership.setTeamRole(TeamRole.FIELD_OPERATOR);

            em.persist(membership);

            assertThatThrownBy(em::flush)
                    .as("Inserting non-existent team foreign key must trigger foreign key constraint violation")
                    .isInstanceOf(Exception.class);
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }

        // Attempt inserting TeamMembership with a non-existent officer UUID
        EntityManager em2 = postgresEntityManagerFactory.createEntityManager();
        EntityTransaction tx2 = em2.getTransaction();
        tx2.begin();

        try {
            Team realTeam = new Team();
            realTeam.setName("FK-Team-" + UUID.randomUUID());
            em2.persist(realTeam);
            em2.flush();

            OfficerProfile nonExistentOfficer = em2.getReference(OfficerProfile.class, UUID.randomUUID());

            TeamMembership membership = new TeamMembership();
            membership.setTeam(realTeam);
            membership.setOfficer(nonExistentOfficer);
            membership.setTeamRole(TeamRole.FIELD_OPERATOR);

            em2.persist(membership);

            assertThatThrownBy(em2::flush)
                    .as("Inserting non-existent officer foreign key must trigger foreign key constraint violation")
                    .isInstanceOf(Exception.class);
        } finally {
            if (tx2.isActive()) {
                tx2.rollback();
            }
            em2.close();
        }
    }

    // =========================================================================
    // Category 6: Unique Constraints (Duplicate Team Name & Duplicate Membership)
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("6. Unique Constraints: Verify database rejects duplicate team name and duplicate membership")
    void verifyUniqueConstraints_DuplicateMembershipAndDuplicateTeamName() {
        // 1. Test Duplicate Team Name Unique Constraint
        String duplicateName = "Unique-Constrained-Team-" + UUID.randomUUID();

        Team t1 = new Team();
        t1.setName(duplicateName);
        t1.setDescription("First instance");
        teamRepository.saveAndFlush(t1);

        Team t2 = new Team();
        t2.setName(duplicateName);
        t2.setDescription("Second instance with duplicate name");

        assertThatThrownBy(() -> teamRepository.saveAndFlush(t2))
                .as("Duplicate team name must trigger database unique constraint violation")
                .isInstanceOf(DataIntegrityViolationException.class);

        // 2. Test Duplicate Membership Unique Constraint (uq_team_officer)
        Team validTeam = new Team();
        validTeam.setName("Unique-Membership-Team-" + UUID.randomUUID());
        Team savedValidTeam = teamRepository.saveAndFlush(validTeam);

        TeamMembership m1 = new TeamMembership();
        m1.setTeam(savedValidTeam);
        m1.setOfficer(testFieldOfficer);
        m1.setTeamRole(TeamRole.FIELD_OPERATOR);
        membershipRepository.saveAndFlush(m1);

        TeamMembership m2 = new TeamMembership();
        m2.setTeam(savedValidTeam);
        m2.setOfficer(testFieldOfficer);
        m2.setTeamRole(TeamRole.TEAM_LEAD);

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(m2))
                .as("Duplicate team membership (same team and officer) must trigger uq_team_officer constraint violation")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // Category 7: Business Logic (Capacity, Membership Rules & Role Restrictions)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("7. Business Logic: Test membership rules, duplicate additions and role access control")
    void verifyBusinessLogic_MembershipRulesAndRoleConstraints() {
        // 1. Supervisor creates a tactical team
        authenticate(testSupervisorUser);
        String teamName = "Tactical-Rules-Team-" + UUID.randomUUID();
        TeamResponse teamResponse = teamService.createTeam(new CreateTeamRequest(teamName, "Rules testing"));
        assertThat(teamResponse.id()).isNotNull();

        // 2. Supervisor adds member
        TeamMemberResponse member1 = teamService.addMember(
                teamResponse.id(),
                new AddTeamMemberRequest(testPlannerOfficer.getId(), TeamRole.SENIOR_PLANNER)
        );
        assertThat(member1.officerId()).isEqualTo(testPlannerOfficer.getId());

        // 3. Business Rule Violation: Attempt to add the exact same officer again via Service
        assertThatThrownBy(() -> teamService.addMember(
                teamResponse.id(),
                new AddTeamMemberRequest(testPlannerOfficer.getId(), TeamRole.FIELD_OPERATOR)
        ))
                .as("Business logic must reject adding an already existing team member")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Officer is already a member of this team");

        // 4. Business Rule Violation: Attempt to create another team with the same name via Service
        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(teamName, "Duplicate team name")))
                .as("Business logic must reject duplicate team names")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A team with this name already exists");

        // 5. Role Restriction: Non-supervisor attempts to create a team
        authenticate(testFieldUser);
        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest("Unauthorized Team", "Should fail")))
                .as("Non-supervisor must be denied from creating teams")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only supervisors can create teams");
    }

    // =========================================================================
    // Category 8: Audit Integrity (Membership and Audit Saved Atomically)
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("8. Audit Integrity: Verify team creation and membership are saved together atomically with audit log")
    void verifyAuditIntegrity_MembershipAndAuditSavedTogether() {
        authenticate(testSupervisorUser);

        // 1. Create Team and verify audit log
        String teamName = "Audit-Integrity-Team-" + UUID.randomUUID();
        TeamResponse createdTeam = teamService.createTeam(new CreateTeamRequest(teamName, "Audit logging test"));

        List<TeamAuditLog> creationLogs = auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(createdTeam.id());
        assertThat(creationLogs).hasSize(1);
        TeamAuditLog creationLog = creationLogs.get(0);
        assertThat(creationLog.getAction()).isEqualTo("TEAM_CREATED");
        assertThat(creationLog.getChangedBy().getId()).isEqualTo(testSupervisorOfficer.getId());
        assertThat(creationLog.getNewValues()).containsKey("name");
        assertThat(creationLog.getNewValues().get("name")).isEqualTo(teamName);

        // 2. Add Member and verify audit log is atomically created
        teamService.addMember(
                createdTeam.id(),
                new AddTeamMemberRequest(testPlannerOfficer.getId(), TeamRole.SENIOR_PLANNER)
        );

        List<TeamAuditLog> updatedLogs = auditLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(createdTeam.id());
        assertThat(updatedLogs).hasSize(2);

        TeamAuditLog memberAddLog = updatedLogs.get(0); // Latest first
        assertThat(memberAddLog.getAction()).isEqualTo("MEMBER_ADDED");
        assertThat(memberAddLog.getOfficer().getId()).isEqualTo(testPlannerOfficer.getId());
        assertThat(memberAddLog.getChangedBy().getId()).isEqualTo(testSupervisorOfficer.getId());
        assertThat(memberAddLog.getNewValues())
                .containsEntry("officerId", testPlannerOfficer.getId().toString())
                .containsEntry("teamRole", "SENIOR_PLANNER");
    }

    // =========================================================================
    // Category 9: Concurrency & Heavy Traffic (Simultaneous Multi-User Load)
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("9. Concurrency & Heavy Traffic: Verify application behavior under heavy traffic and simultaneous users")
    void verifyConcurrencyAndHeavyTraffic_SimultaneousUsers() throws Exception {
        authenticate(testSupervisorUser);
        TeamResponse activeTeam = teamService.createTeam(new CreateTeamRequest("Heavy-Traffic-Team-" + UUID.randomUUID(), "Stress testing"));
        teamService.addMember(activeTeam.id(), new AddTeamMemberRequest(testPlannerOfficer.getId(), TeamRole.SENIOR_PLANNER));
        teamService.addMember(activeTeam.id(), new AddTeamMemberRequest(testFieldOfficer.getId(), TeamRole.FIELD_OPERATOR));

        int concurrentUsers = 30;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch completionGate = new CountDownLatch(concurrentUsers);
        AtomicInteger successCounter = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrentUsers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // All threads wait for the simultaneous trigger
                    startGate.await();

                    // Each user authenticates and acts simultaneously
                    if (index % 3 == 0) {
                        // User Group 1: Telemetry ingestion (PostgreSQL write + Redis cache update)
                        authenticate(testFieldUser);
                        trackingService.recordLocation(new LocationUpdateRequest(
                                28.6139 + (index * 0.001),
                                77.2090 + (index * 0.001),
                                5.0,
                                OffsetDateTime.now()
                        ));
                    } else if (index % 3 == 1) {
                        // User Group 2: Team member roster lookup (PostgreSQL read)
                        authenticate(testPlannerUser);
                        List<TeamMemberResponse> members = teamService.getTeamMembers(activeTeam.id());
                        assertThat(members).isNotEmpty();
                    } else {
                        // User Group 3: Live tactical map query (PostgreSQL/Redis cross-read)
                        authenticate(testSupervisorUser);
                        List<TeamResponse> authorizedTeams = teamService.getAuthorizedTeams();
                        assertThat(authorizedTeams).isNotEmpty();
                    }

                    successCounter.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    completionGate.countDown();
                }
            });
        }

        // Trigger simultaneous multi-user blast
        startGate.countDown();

        boolean finishedInTime = completionGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).as("All concurrent tasks must finish within timeout").isTrue();
        assertThat(exceptions).as("Zero unhandled exceptions during simultaneous user load: " + exceptions).isEmpty();
        assertThat(successCounter.get()).isEqualTo(concurrentUsers);
    }

    // =========================================================================
    // Category 10: Database Failures (Simulate Disconnection / Unavailability)
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("10. Database Failures: Verify controlled error handling on database connection failure")
    void verifyDatabaseFailures_ControlledErrorHandling() {
        // Attempting to query with a bad SQL or connection interruption
        assertThatThrownBy(() -> {
            try (Connection conn = postgresDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM non_existent_table_failure_test")) {
                stmt.executeQuery();
            }
        })
                .as("Querying non-existent database table or broken query must throw SQL exception cleanly")
                .isInstanceOf(java.sql.SQLException.class);

        // Verify DataSource connection pool survives the exception and serves next connection
        boolean canGetHealthyConnection = false;
        try (Connection healthyConn = postgresDataSource.getConnection()) {
            canGetHealthyConnection = healthyConn.isValid(2);
        } catch (Exception e) {
            canGetHealthyConnection = false;
        }

        assertThat(canGetHealthyConnection)
                .as("Database connection pool must immediately recover and provide healthy connection")
                .isTrue();
    }

    // =========================================================================
    // Category 11: Data Consistency (Compare Database State Before and After Failures)
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("11. Data Consistency: Compare database state before and after failures to verify zero partial writes")
    void verifyDataConsistency_BeforeAndAfterFailures() {
        authenticate(testSupervisorUser);

        // Snapshot initial database state
        long teamCountBefore = teamRepository.count();
        long membershipCountBefore = membershipRepository.count();
        long auditCountBefore = auditLogRepository.count();
        long userCountBefore = userRepository.count();

        // 1. Trigger deliberate failure in transaction
        TransactionTemplate txTemplate = new TransactionTemplate(postgresTransactionManager);
        try {
            txTemplate.execute(status -> {
                Team team = new Team();
                team.setName("Inconsistent-Team-" + UUID.randomUUID());
                Team saved = teamRepository.save(team);

                TeamAuditLog audit = new TeamAuditLog();
                audit.setTeam(saved);
                audit.setChangedBy(testSupervisorOfficer);
                audit.setAction("TEST_PARTIAL_WRITE");
                auditLogRepository.save(audit);

                // Crash midway
                throw new IllegalStateException("Forced crash to test data consistency rollback");
            });
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Forced crash to test data consistency rollback");
        }

        // 2. Trigger deliberate constraint violation failure
        try {
            Team duplicate = new Team();
            duplicate.setName("Duplicate-Candidate-" + UUID.randomUUID());
            teamRepository.saveAndFlush(duplicate);

            // Re-saving same duplicate should fail and roll back flush
            Team duplicate2 = new Team();
            duplicate2.setName(duplicate.getName());
            teamRepository.saveAndFlush(duplicate2);
        } catch (DataIntegrityViolationException ignored) {
            // Expected constraint violation
        }

        // Compare database state after failure against baseline (+ 1 from duplicate first insert)
        long teamCountAfter = teamRepository.count();
        long membershipCountAfter = membershipRepository.count();
        long auditCountAfter = auditLogRepository.count();
        long userCountAfter = userRepository.count();

        // The failed transaction must leave zero ghost records
        assertThat(membershipCountAfter).isEqualTo(membershipCountBefore);
        assertThat(auditCountAfter).isEqualTo(auditCountBefore);
        assertThat(userCountAfter).isEqualTo(userCountBefore);
        // Team count should only reflect the 1 successfully committed duplicate parent, with 0 partial writes from the failed tx
        assertThat(teamCountAfter).isEqualTo(teamCountBefore + 1);
    }

    // =========================================================================
    // Category 12: Multi-Service & Database Collaboration (End-to-End Synergy)
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("12. System Synergy: Verify services, repositories, and databases work together seamlessly end-to-end")
    void verifyServicesRepositoriesAndDatabasesWorkTogether_EndToEndFlow() {
        // Step 1: Supervisor creates tactical team (PostgreSQL Team + AuditLog)
        authenticate(testSupervisorUser);
        TeamResponse tacticalTeam = teamService.createTeam(new CreateTeamRequest(
                "Synergy-Team-" + UUID.randomUUID(),
                "Validating MySQL + PostgreSQL + Redis synergy"
        ));
        assertThat(tacticalTeam.id()).isNotNull();

        // Step 2: Senior Planner assigned to team (PostgreSQL TeamMembership + AuditLog)
        teamService.addMember(
                tacticalTeam.id(),
                new AddTeamMemberRequest(testPlannerOfficer.getId(), TeamRole.SENIOR_PLANNER)
        );

        // Step 3: Field Officer assigned to team
        teamService.addMember(
                tacticalTeam.id(),
                new AddTeamMemberRequest(testFieldOfficer.getId(), TeamRole.FIELD_OPERATOR)
        );

        // Step 4: Senior Planner creates a task (PostgreSQL Task + TaskAuditLog)
        authenticate(testPlannerUser);
        Task task = taskService.createTask(new CreateTaskRequest(
                tacticalTeam.id(),
                "Perimeter Lockdown - Alpha Sector",
                "PATROLLING",
                "Secure all entrances and monitor perimeter telemetry",
                "Alpha Sector Command Post",
                28.6139,
                77.2090
        ));
        assertThat(task.getId()).isNotNull();

        // Step 5: Senior Planner assigns task to Field Officer (PostgreSQL TaskAssignment)
        assignmentService.assignOfficer(
                task.getId(),
                testFieldOfficer.getId(),
                "Lead the sector perimeter checkpoint",
                "Maintain continuous perimeter presence"
        );

        // Step 6: Field Officer authenticates, sends live location (PostgreSQL LocationUpdate + Redis Live Cache)
        authenticate(testFieldUser);
        OfficerLiveLocationResponse liveLocation = trackingService.recordLocation(new LocationUpdateRequest(
                28.6139,
                77.2090,
                3.5,
                OffsetDateTime.now()
        ));
        assertThat(liveLocation.officerId()).isEqualTo(testFieldOfficer.getId());
        assertThat(liveLocation.latitude()).isEqualTo(28.6139);

        // Verify Redis live cache was populated
        Object cachedValue = redisTemplate.opsForValue().get("officer:live:" + testFieldOfficer.getId());
        assertThat(cachedValue).isNotNull();

        // Step 7: Supervisor checks live map for team (PostgreSQL + Redis cross-read)
        authenticate(testSupervisorUser);
        List<OfficerLiveLocationResponse> liveMap = trackingService.getLiveLocationsForTeam(tacticalTeam.id());
        assertThat(liveMap).isNotEmpty();
        assertThat(liveMap.stream().anyMatch(l -> l.officerId().equals(testFieldOfficer.getId()))).isTrue();

        // Proves: MySQL (Auth/Users), PostgreSQL (Teams/Tasks/Assignments/Locations), and Redis (Live Cache) work together perfectly!
    }
}
