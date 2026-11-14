package com.substring.auth.app.auth.config;

import com.substring.auth.app.auth.entities.Provider;
import com.substring.auth.app.auth.entities.RefreshToken;
import com.substring.auth.app.auth.entities.Role;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RefreshTokenRepository;
import com.substring.auth.app.auth.repositories.RoleRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.impl.CookieService;
import com.substring.auth.app.auth.services.impl.JwtService;
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

            Role defaultRole = roleRepository.findByName(AppConstants.DEFAULT_ROLE)
                    .or(() -> roleRepository.findByName("Not Assigned"))
                    .or(() -> roleRepository.findByName("NOT_ASSIGNED"))
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setName(AppConstants.DEFAULT_ROLE);
                        return roleRepository.save(r);
                    });

            User user;
            switch (registrationId.toLowerCase()) {
                case "github" -> {
                    String githubId = oAuth2User.getAttributes().getOrDefault("id", "").toString().trim();
                    String login = oAuth2User.getAttributes().getOrDefault("login", "").toString().trim();
                    String name = oAuth2User.getAttributes().getOrDefault("name", login).toString().trim();
                    String image = oAuth2User.getAttributes().getOrDefault("avatar_url", "").toString().trim();

                    String email = (String) oAuth2User.getAttributes().get("email");
                    if (email == null || email.isBlank()) {
                        email = login + "@github.com";
                    }

                    if (githubId.isBlank()) {
                        logger.warn("Security Alert: GitHub OAuth2 sign-in rejected - missing 'id' attribute");
                        throw new IllegalArgumentException("GitHub account identifier is missing");
                    }

                    // 1. Primary Matching by GitHub stable ID
                    Optional<User> existingUserByProvider = userRepository.findByProviderAndProviderId(Provider.GITHUB, githubId);

                    if (existingUserByProvider.isPresent()) {
                        User matchedUser = existingUserByProvider.get();
                        logger.info("OAuth2 login matched via GitHub stable ID ({}) for user ID: {}", githubId, matchedUser.getId());
                        if (!name.isBlank()) {
                            matchedUser.setName(name);
                        }
                        if (!image.isBlank()) {
                            matchedUser.setImage(image);
                        }
                        user = userRepository.save(matchedUser);
                    } else {
                        Optional<User> existingUserByEmail = userRepository.findByEmail(email);
                        if (existingUserByEmail.isPresent()) {
                            User existingUser = existingUserByEmail.get();

                            if (existingUser.getProvider() == Provider.GITHUB && existingUser.getProviderId() != null && !existingUser.getProviderId().equals(githubId)) {
                                logger.error("Security Alert: GitHub ID mismatch for email '{}'. Aborting authentication.", email);
                                throw new SecurityException("Account already linked to a different GitHub identity.");
                            }

                            if (existingUser.getProvider() != null && existingUser.getProvider() != Provider.LOCAL && existingUser.getProvider() != Provider.GITHUB) {
                                logger.error("Security Alert: Provider collision for email '{}'. Aborting.", email);
                                throw new SecurityException("Account was registered via " + existingUser.getProvider() + ". Please sign in using your original provider.");
                            }

                            existingUser.setProvider(Provider.GITHUB);
                            existingUser.setProviderId(githubId);
                            user = userRepository.save(existingUser);
                        } else {
                            User newUser = User.builder()
                                    .email(email)
                                    .name(name.isBlank() ? login : name)
                                    .policePost("Field Officer")
                                    .role(defaultRole)
                                    .image(image)
                                    .enable(true)
                                    .provider(Provider.GITHUB)
                                    .providerId(githubId)
                                    .build();
                            user = userRepository.save(newUser);
                        }
                    }
                }

                default -> {
                    logger.warn("OAuth2 sign-in rejected: Unsupported provider '{}'", registrationId);
                    throw new IllegalArgumentException("Unsupported OAuth2 registration provider: " + registrationId);
                }
            }

            if (!user.isEnabled()) {
                logger.warn("OAuth2 login rejected: Account is disabled for user ID: {}", user.getId());
                throw new SecurityException("User account is disabled. Contact your commanding officer.");
            }

            String jti = UUID.randomUUID().toString();
            RefreshToken refreshTokenOb = RefreshToken.builder()
                    .jti(jti)
                    .user(user)
                    .revoked(false)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                    .build();

            refreshTokenRepository.save(refreshTokenOb);

            String refreshToken = jwtService.generateRefreshToken(user, refreshTokenOb.getJti());
            cookieService.attachRefreshCookie(response, refreshToken, (int) jwtService.getRefreshTtlSeconds());

            logger.info("OAuth2 login completed successfully for user ID: {}", user.getId());
            response.sendRedirect(frontEndSuccessUrl);

        } catch (Exception ex) {
            logger.error("OAuth2 authentication aborted due to security validation failure: {}", ex.getMessage());
            String redirectUrl = frontEndFailureUrl;
            String encodedMsg = URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "error=" + encodedMsg;
            response.sendRedirect(redirectUrl);
        }
    }
}
