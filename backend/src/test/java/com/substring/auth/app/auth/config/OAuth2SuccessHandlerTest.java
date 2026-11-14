package com.substring.auth.app.auth.config;

import com.substring.auth.app.auth.entities.Provider;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RefreshTokenRepository;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.CookieService;
import com.substring.auth.app.auth.services.impl.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private CookieService cookieService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OAuth2User oAuth2User;

    @Mock
    private OAuth2AuthenticationToken authentication;

    @InjectMocks
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    private Role defaultRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oAuth2SuccessHandler, "frontEndSuccessUrl", "http://localhost:5173/oauth/success");
        ReflectionTestUtils.setField(oAuth2SuccessHandler, "frontEndFailureUrl", "http://localhost:5173/login?error=oauth_failed");

        defaultRole = new Role();
        defaultRole.setId(UUID.randomUUID());
        defaultRole.setName(AppConstants.DEFAULT_ROLE);

        lenient().when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        lenient().when(authentication.getPrincipal()).thenReturn(oAuth2User);
        lenient().when(roleRepository.findByName(any())).thenReturn(Optional.of(defaultRole));
        lenient().when(jwtService.getRefreshTtlSeconds()).thenReturn(86400L);
    }

    @Test
    @DisplayName("Security: Reject Google login when email_verified claim is false (Prevents Account Takeover)")
    void shouldRejectGoogleLoginWhenEmailNotVerified() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-attacker-sub-123");
        attributes.put("email", "victim.commander@police.gov");
        attributes.put("name", "Attacker Impersonator");
        attributes.put("email_verified", false); // Unverified email claim

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        // Verify authentication was aborted and redirected to failure URL
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("http://localhost:5173/login?error=oauth_failed");
        assertThat(redirectCaptor.getValue()).contains("email+is+not+verified");

        // Verify NO tokens or cookies were generated
        verify(jwtService, never()).generateRefreshToken(any(), any());
        verify(cookieService, never()).attachRefreshCookie(any(), any(), anyInt());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security: Reject Google login when email_verified claim is missing/null")
    void shouldRejectGoogleLoginWhenEmailVerifiedClaimMissing() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-attacker-sub-999");
        attributes.put("email", "victim.officer@police.gov");
        attributes.put("name", "Unknown Account");
        // email_verified is not present in attributes

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("email+is+not+verified");

        verify(jwtService, never()).generateRefreshToken(any(), any());
    }

    @Test
    @DisplayName("Stable ID: Authenticate existing Google user matching Provider.GOOGLE + providerId")
    void shouldAuthenticateUserByStableGoogleProviderId() throws Exception {
        String googleSub = "1098234723498234";
        String email = "verified.officer@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", googleSub);
        attributes.put("email", email);
        attributes.put("name", "Inspector Sharma");
        attributes.put("picture", "http://image.url/avatar.jpg");
        attributes.put("email_verified", true);

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Inspector Sharma")
                .provider(Provider.GOOGLE)
                .providerId(googleSub)
                .enable(true)
                .role(defaultRole)
                .policePost("Central Police Post")
                .build();

        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, googleSub)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository).findByProviderAndProviderId(Provider.GOOGLE, googleSub);
        verify(cookieService).attachRefreshCookie(eq(response), eq("mock-refresh-token"), eq(86400));
        verify(response).sendRedirect("http://localhost:5173/oauth/success");
    }

    @Test
    @DisplayName("Security: Reject Google login when email matches existing Google account with a DIFFERENT sub")
    void shouldRejectGoogleLoginWhenSubMismatchesExistingGoogleAccount() throws Exception {
        String originalGoogleSub = "google-sub-original-111";
        String attackerGoogleSub = "google-sub-attacker-222";
        String email = "commander@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", attackerGoogleSub);
        attributes.put("email", email);
        attributes.put("name", "Attacker");
        attributes.put("email_verified", true);

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        // Not found by the attacker's sub
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, attackerGoogleSub)).thenReturn(Optional.empty());

        // Existing user has email but bound to originalGoogleSub
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Legitimate Commander")
                .provider(Provider.GOOGLE)
                .providerId(originalGoogleSub)
                .enable(true)
                .role(defaultRole)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("Account+already+linked+to+a+different+Google+identity");

        verify(jwtService, never()).generateRefreshToken(any(), any());
    }

    @Test
    @DisplayName("Security: Reject Google login when email is bound to another third-party provider (GITHUB)")
    void shouldRejectCrossProviderCollision() throws Exception {
        String googleSub = "google-sub-333";
        String email = "developer.officer@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", googleSub);
        attributes.put("email", email);
        attributes.put("name", "Officer Dev");
        attributes.put("email_verified", true);

        when(oAuth2User.getAttributes()).thenReturn(attributes);
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, googleSub)).thenReturn(Optional.empty());

        User existingGithubUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Officer Dev")
                .provider(Provider.GITHUB)
                .providerId("github-id-999")
                .enable(true)
                .role(defaultRole)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingGithubUser));

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("Account+was+registered+via+GITHUB");

        verify(jwtService, never()).generateRefreshToken(any(), any());
    }

    @Test
    @DisplayName("Safe Linking: Allow linking Google ID to existing LOCAL user if email_verified is true")
    void shouldLinkGoogleToLocalUserWhenEmailVerified() throws Exception {
        String googleSub = "google-sub-555";
        String email = "local.user@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", googleSub);
        attributes.put("email", email);
        attributes.put("name", "Local Officer");
        attributes.put("email_verified", true);

        when(oAuth2User.getAttributes()).thenReturn(attributes);
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, googleSub)).thenReturn(Optional.empty());

        User localUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Local Officer")
                .provider(Provider.LOCAL)
                .enable(true)
                .role(defaultRole)
                .policePost("Wakad Station")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(localUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        // Verify user was linked to Google with googleSub
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(userCaptor.getValue().getProviderId()).isEqualTo(googleSub);

        verify(cookieService).attachRefreshCookie(eq(response), eq("mock-refresh-token"), eq(86400));
        verify(response).sendRedirect("http://localhost:5173/oauth/success");
    }

    @Test
    @DisplayName("New User: Register new user with verified Google identity")
    void shouldRegisterNewUserWithVerifiedGoogleIdentity() throws Exception {
        String googleSub = "google-sub-777";
        String email = "new.officer@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", googleSub);
        attributes.put("email", email);
        attributes.put("name", "New Officer");
        attributes.put("picture", "http://image.url/pic.jpg");
        attributes.put("email_verified", true);

        when(oAuth2User.getAttributes()).thenReturn(attributes);
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, googleSub)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("New Officer")
                .provider(Provider.GOOGLE)
                .providerId(googleSub)
                .enable(true)
                .role(defaultRole)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(email);
        assertThat(userCaptor.getValue().getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(userCaptor.getValue().getProviderId()).isEqualTo(googleSub);

        verify(response).sendRedirect("http://localhost:5173/oauth/success");
    }
}
