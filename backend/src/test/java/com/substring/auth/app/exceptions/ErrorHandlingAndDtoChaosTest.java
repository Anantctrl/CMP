package com.substring.auth.app.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.payload.LoginRequest;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.dtos.ApiError;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Global Error Handling, DTOs & Exception Translation - Chaos & Destruction Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ErrorHandlingAndDtoChaosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        Role officerRole = roleRepository.findByName("Not_Assigned")
                .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("Not_Assigned");
                    return roleRepository.save(r);
                });

        testUser = new User();
        testUser.setEmail("err_test_" + UUID.randomUUID() + "@police.gov");
        testUser.setPassword(passwordEncoder.encode("SecurePass123!"));
        testUser.setName("Error Officer");
        testUser.setRole(officerRole);
        testUser.setPolicePost("Constable");
        testUser.setEnable(true);
        userRepository.save(testUser);

        userToken = jwtService.generateAccessToken(testUser);
    }

    // =========================================================================
    // SECTION 1: MALFORMED JSON & JACKSON UNREADABLE PAYLOADS
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Malformed JSON: Unparseable JSON structure returns 400 'Malformed JSON'")
    void shouldHandleMalformedJsonPayloadGracefully() throws Exception {
        String brokenJson = "{\"title\": \"Broken JSON\", \"incomplete\": [1, 2, ";

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brokenJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed JSON"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request payload or unsupported data type"))
                .andExpect(jsonPath("$.path").value("/api/v1/teams"));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Empty Request Body: Blank request body on endpoints expecting DTO returns 400 'Malformed JSON'")
    void shouldHandleEmptyRequestBodyOnPost() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed JSON"));
    }

    // =========================================================================
    // SECTION 2: PARAMETER TYPE MISMATCH
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("2.1 Type Mismatch: Non-UUID string passed to UUID path variable returns 400 'Type Mismatch'")
    void shouldHandlePathVariableTypeMismatchGracefully() throws Exception {
        mockMvc.perform(get("/api/v1/teams/not-a-valid-uuid")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Type Mismatch"))
                .andExpect(jsonPath("$.message").value("Parameter 'teamId' should be of type 'UUID'"));
    }

    // =========================================================================
    // SECTION 3: BEAN VALIDATION & DTO ERROR MAPPING
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("3.1 Bean Validation: Missing required fields mapped to validationErrors dictionary")
    void shouldMapValidationErrorsCleanlyIntoValidationErrorsField() throws Exception {
        // Missing name on CreateTeamRequest
        String missingFieldsJson = "{}";

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingFieldsJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Request validation failed on one or more fields"))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    // =========================================================================
    // SECTION 4: NOT FOUND (404), ACCESS DENIED (403) & UNAUTHORIZED (401)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("4.1 Resource Not Found: Querying non-existent entity returns 404 'Not Found'")
    void shouldReturn404WhenEntityNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/teams/" + nonExistentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/teams/" + nonExistentId));
    }

    @Test
    @Order(6)
    @DisplayName("4.2 Bad Credentials: Invalid login attempt returns 401 'Unauthorized'")
    void shouldReturn401OnBadCredentials() throws Exception {
        LoginRequest req = new LoginRequest(testUser.getEmail(), "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(7)
    @DisplayName("4.3 Access Denied: Unauthorized role access returns 403 'Forbidden'")
    void shouldReturn403OnAccessDenied() throws Exception {
        mockMvc.perform(post("/api/v1/officers/cache/evict")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // =========================================================================
    // SECTION 5: SECURITY OF ERROR DTO (NO STACK TRACE LEAKS)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("5.1 DTO Security: ApiError payload never exposes internal Java stack trace or raw SQL")
    void shouldEnsureApiErrorNeverExposesStackTraceOrSqlTraces() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/teams/invalid-uuid-chaos-test")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = res.getResponse().getContentAsString();
        ApiError apiError = objectMapper.readValue(responseBody, ApiError.class);

        assertThat(apiError.status()).isEqualTo(400);
        assertThat(apiError.error()).isEqualTo("Type Mismatch");
        assertThat(responseBody)
                .as("Error response body must not leak Java package internals or stack traces")
                .doesNotContain("org.springframework")
                .doesNotContain("java.lang")
                .doesNotContain("SqlExceptionHelper")
                .doesNotContain("StackTrace");
    }
}
