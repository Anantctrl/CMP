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

        lenient().when(authentication.getPrincipal()).thenReturn(oAuth2User);
        lenient().when(roleRepository.findByName(any())).thenReturn(Optional.of(defaultRole));
        lenient().when(jwtService.getRefreshTtlSeconds()).thenReturn(86400L);
    }

    @Test
    @DisplayName("Google Auth Removed: Reject Google login as unsupported registration provider")
    void shouldRejectGoogleLoginAsUnsupported() throws Exception {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("http://localhost:5173/login?error=oauth_failed");
        assertThat(redirectCaptor.getValue()).contains("Unsupported+OAuth2+registration+provider%3A+google");

        verify(jwtService, never()).generateRefreshToken(any(), any());
        verify(cookieService, never()).attachRefreshCookie(any(), any(), anyInt());
    }

    @Test
    @DisplayName("GitHub Auth: Authenticate existing user matching stable GitHub ID")
    void shouldAuthenticateUserByStableGithubProviderId() throws Exception {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");

        String githubId = "github-stable-id-12345";
        String email = "inspector@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", githubId);
        attributes.put("login", "inspector_sharma");
        attributes.put("name", "Inspector Sharma");
        attributes.put("avatar_url", "http://avatars.github.com/u/12345");
        attributes.put("email", email);

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Inspector Sharma")
                .provider(Provider.GITHUB)
                .providerId(githubId)
                .enable(true)
                .role(defaultRole)
                .policePost("Field Officer")
                .build();

        when(userRepository.findByProviderAndProviderId(Provider.GITHUB, githubId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-github-refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository).findByProviderAndProviderId(Provider.GITHUB, githubId);
        verify(cookieService).attachRefreshCookie(eq(response), eq("mock-github-refresh-token"), eq(86400));
        verify(response).sendRedirect("http://localhost:5173/oauth/success");
    }

    @Test
    @DisplayName("GitHub Auth: Register new user with stable GitHub ID")
    void shouldRegisterNewUserWithGithubIdentity() throws Exception {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");

        String githubId = "github-new-id-99999";
        String email = "new.officer@police.gov";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", githubId);
        attributes.put("login", "new_officer");
        attributes.put("name", "New Officer");
        attributes.put("avatar_url", "http://avatars.github.com/u/99999");
        attributes.put("email", email);

        when(oAuth2User.getAttributes()).thenReturn(attributes);
        when(userRepository.findByProviderAndProviderId(Provider.GITHUB, githubId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("New Officer")
                .provider(Provider.GITHUB)
                .providerId(githubId)
                .enable(true)
                .role(defaultRole)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-new-refresh-token");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(email);
        assertThat(userCaptor.getValue().getProvider()).isEqualTo(Provider.GITHUB);
        assertThat(userCaptor.getValue().getProviderId()).isEqualTo(githubId);

        verify(response).sendRedirect("http://localhost:5173/oauth/success");
    }

    @Test
    @DisplayName("Security: Reject disabled account during OAuth login")
    void shouldRejectDisabledAccountDuringOAuthLogin() throws Exception {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");

        String githubId = "github-disabled-user-777";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", githubId);
        attributes.put("login", "disabled_officer");
        attributes.put("email", "disabled@police.gov");

        when(oAuth2User.getAttributes()).thenReturn(attributes);

        User disabledUser = User.builder()
                .id(UUID.randomUUID())
                .email("disabled@police.gov")
                .name("Disabled Officer")
                .provider(Provider.GITHUB)
                .providerId(githubId)
                .enable(false) // Disabled
                .role(defaultRole)
                .build();

        when(userRepository.findByProviderAndProviderId(Provider.GITHUB, githubId)).thenReturn(Optional.of(disabledUser));
        when(userRepository.save(any(User.class))).thenReturn(disabledUser);

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("User+account+is+disabled");

        verify(jwtService, never()).generateRefreshToken(any(), any());
    }
}
