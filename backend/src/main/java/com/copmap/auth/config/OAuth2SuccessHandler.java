package com.copmap.auth.config;

import com.copmap.auth.entities.Provider;
import com.copmap.auth.entities.RefreshToken;
import com.copmap.auth.entities.Role;
import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.RefreshTokenRepository;
import com.copmap.auth.repositories.RoleRepository;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.auth.services.impl.CookieService;
import com.copmap.auth.services.impl.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.auth.frontend.success-redirect}")
    private String frontEndSuccessUrl;

    @Value("${app.auth.frontend.failure-redirect:http://localhost:5173/login?error=oauth_failed}")
    private String frontEndFailureUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

            String registrationId = "unknown";
            if (authentication instanceof OAuth2AuthenticationToken token) {
                registrationId = token.getAuthorizedClientRegistrationId();
            }

            logger.info("OAuth2 login initiated via provider: {}", registrationId);

            logger.warn("OAuth2 sign-in rejected: Unsupported provider '{}'. OAuth2 logins are disabled.", registrationId);
            throw new IllegalArgumentException("Unsupported OAuth2 registration provider: " + registrationId);

        } catch (Exception ex) {
            logger.error("OAuth2 authentication aborted due to security validation failure: {}", ex.getMessage());
            String redirectUrl = frontEndFailureUrl;
            String encodedMsg = URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "error=" + encodedMsg;
            response.sendRedirect(redirectUrl);
        }
    }
}
