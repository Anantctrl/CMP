package com.substring.auth.app.auth;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.payload.LoginRequest;
import com.substring.auth.app.auth.payload.UserDto;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth Failure, Security & Cryptography Unit Test Suite")
public class AuthFailureAndSecurityUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User supervisorUser;
    private User officerUser;
    private User disabledUser;
    private String supervisorToken;
    private String officerToken;

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

        // 1. Supervisor
        supervisorUser = new User();
        supervisorUser.setEmail("sup.test." + UUID.randomUUID() + "@copmap.gov");
        supervisorUser.setName("Commander Supervisor");
        supervisorUser.setPassword(passwordEncoder.encode("SecurePass123!"));
        supervisorUser.setPolicePost("Central HQ");
        supervisorUser.setRole(supervisorRole);
        supervisorUser.setEnable(true);
        supervisorUser = userRepository.save(supervisorUser);
        supervisorToken = jwtService.generateAccessToken(supervisorUser);

        // 2. Regular Officer
        officerUser = new User();
        officerUser.setEmail("off.test." + UUID.randomUUID() + "@copmap.gov");
        officerUser.setName("Field Officer");
        officerUser.setPassword(passwordEncoder.encode("SecurePass123!"));
        officerUser.setPolicePost("North Beat");
        officerUser.setRole(officerRole);
        officerUser.setEnable(true);
        officerUser = userRepository.save(officerUser);
        officerToken = jwtService.generateAccessToken(officerUser);

        // 3. Disabled Account
        disabledUser = new User();
        disabledUser.setEmail("dis.test." + UUID.randomUUID() + "@copmap.gov");
        disabledUser.setName("Suspended Officer");
        disabledUser.setPassword(passwordEncoder.encode("SecurePass123!"));
        disabledUser.setPolicePost("Suspended");
        disabledUser.setRole(officerRole);
        disabledUser.setEnable(false); // Explicitly disabled
        disabledUser = userRepository.save(disabledUser);
    }

    @Nested
    @DisplayName("BCrypt Password Hashing Failure Points")
    class BCryptFailureTests {

        @Test
        @DisplayName("Verify BCrypt Rejects Incorrect Passwords and Empty Hashes")
        void shouldFailOnPasswordMismatch() {
            String rawPassword = "CorrectPassword123!";
            String hash = passwordEncoder.encode(rawPassword);

            assertThat(passwordEncoder.matches("WrongPassword", hash)).isFalse();
            assertThat(passwordEncoder.matches("", hash)).isFalse();
            assertThat(passwordEncoder.matches("correctpassword123!", hash)).isFalse(); // Case-sensitive
        }

        @Test
        @DisplayName("Verify BCrypt Handles Malformed or Corrupted Hashes Safely")
        void shouldHandleMalformedHashGracefully() {
            String rawPassword = "TestPassword";
            String corruptedHash = "$2a$10$invalidhashstringthatisnotavalidbcyrptpayload";

            // Standard BCrypt returns false or handles invalid salt safely without JVM crash
            assertThat(passwordEncoder.matches(rawPassword, corruptedHash)).isFalse();
        }
    }

    @Nested
    @DisplayName("JWT Token Issuance, Parsing & Verification Failures")
    class JwtFailureTests {

        @Test
        @DisplayName("Verify Expired JWT Token Throws ExpiredJwtException on Verification")
        void shouldThrowExpiredJwtExceptionOnExpiredToken() {
            // Instantiate a short-lived JwtService with 0-second TTL
            String validSecret = "a_very_secret_key_that_is_at_least_64_bytes_long_for_hmac_sha512_security_standard_1234567890";
            JwtService shortLivedJwtService = new JwtService(validSecret, -5, 60, "copmap-test");

            String expiredToken = shortLivedJwtService.generateAccessToken(officerUser);

            assertThatThrownBy(() -> shortLivedJwtService.parse(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("Verify Tampered JWT Payload Signature Fails Verification")
        void shouldFailOnTamperedSignature() {
            String validToken = jwtService.generateAccessToken(officerUser);

            // Tamper token payload segment (replace header.payload.signature)
            String[] parts = validToken.split("\\.");
            assertThat(parts.length).isEqualTo(3);

            // Change a character in the payload section
            String tamperedPayload = parts[1].substring(0, parts[1].length() - 2) + "==";
            String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

            assertThatThrownBy(() -> jwtService.parse(tamperedToken))
                    .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("Verify Malformed Non-JWT Strings Throw MalformedJwtException")
        void shouldFailOnMalformedJwtString() {
            assertThatThrownBy(() -> jwtService.parse("not.a.valid.jwt.token"))
                    .isInstanceOf(MalformedJwtException.class);

            assertThatThrownBy(() -> jwtService.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Verify Refresh Token Cannot Be Used As Access Token")
        void shouldRejectRefreshTokenAsAccessToken() {
            String refreshToken = jwtService.generateRefreshToken(officerUser, UUID.randomUUID().toString());

            assertThat(jwtService.isRefreshToken(refreshToken)).isTrue();
            assertThat(jwtService.isAccessToken(refreshToken)).isFalse();
        }

        @Test
        @DisplayName("Verify JwtService Rejects Insecure Secrets Shorter than 64 Bytes")
        void shouldRejectShortJwtSecret() {
            assertThatThrownBy(() -> new JwtService("short-secret-under-64-bytes", 3600, 86400, "test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid secret");
        }
    }

    @Nested
    @DisplayName("Jackson Serialization Failure Points")
    class JacksonFailureTests {

        @Test
        @DisplayName("Verify Malformed JSON Body Triggers Deserialization Failure")
        void shouldFailOnMalformedJsonSyntax() {
            String brokenJson = "{\"email\": \"missing-closing-brace";

            assertThatThrownBy(() -> objectMapper.readValue(brokenJson, LoginRequest.class))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }

        @Test
        @DisplayName("Verify Incompatible Type Mapping Triggers HTTP 400 Malformed JSON Response")
        void shouldReturn400OnIncompatibleJsonType() throws Exception {
            // Passing a boolean array where a string is expected
            String invalidTypeJson = "{\"email\": [true, false], \"password\": 12345}";

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidTypeJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Malformed JSON"));
        }
    }

    @Nested
    @DisplayName("Security & Role-Based Access Control (RBAC) Failures")
    class SecurityAndRbacFailureTests {

        @Test
        @DisplayName("Verify Unauthenticated Request to Protected Route Returns HTTP 401")
        void shouldReturn401WhenNoTokenProvided() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized Access"));
        }

        @Test
        @DisplayName("Verify Role Failure: Regular Officer Accessing Supervisor Route Returns HTTP 403")
        void shouldReturn403WhenOfficerAccessesSupervisorRestrictedUrl() throws Exception {
            // /api/v1/users/** is restricted to Supervisor and Administrator
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + officerToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden Access"));
        }

        @Test
        @DisplayName("Verify Authorized Role: Supervisor Successfully Accesses Restricted Route")
        void shouldAllowSupervisorAccessToUsersRoute() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + supervisorToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Verify Disabled Account Login Is Rejected with HTTP 401 Disabled Exception")
        void shouldRejectLoginForDisabledAccount() throws Exception {
            LoginRequest loginReq = new LoginRequest(disabledUser.getEmail(), "SecurePass123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Nested
    @DisplayName("Stateless Session & Filter Validation")
    class StatelessSessionTests {

        @Test
        @DisplayName("Verify Session is Stateless: Mock JSESSIONID Cookie Without Token Returns 401")
        void shouldEnforceStatelessSessionAndRejectSessionCookieWithoutToken() throws Exception {
            Cookie fakeSessionCookie = new Cookie("JSESSIONID", "FORGED-SESSION-ID-12345");

            mockMvc.perform(get("/api/v1/users")
                            .cookie(fakeSessionCookie)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Verify Bearer Token Extraction with Extra Spaces or Broken Scheme Fails Gracefully")
        void shouldIgnoreBrokenAuthorizationHeaders() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Basic dXNlcjpwYXNz") // Wrong scheme
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "BearerBrokenFormat")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
