package com.copmap.auth.config;

import com.copmap.auth.entities.Provider;
import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RefreshTokenRepository;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.auth.services.impl.CookieService;
import com.copmap.auth.services.impl.JwtService;
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
    @DisplayName("GitHub Auth Removed: Reject GitHub login as unsupported registration provider")
    void shouldRejectGithubLoginAsUnsupported() throws Exception {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("http://localhost:5173/login?error=oauth_failed");
        assertThat(redirectCaptor.getValue()).contains("Unsupported+OAuth2+registration+provider%3A+github");

        verify(jwtService, never()).generateRefreshToken(any(), any());
        verify(cookieService, never()).attachRefreshCookie(any(), any(), anyInt());
    }
}
