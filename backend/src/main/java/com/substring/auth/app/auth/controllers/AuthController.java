
package com.substring.auth.app.auth.controllers;

import com.substring.auth.app.auth.payload.LoginRequest;
import com.substring.auth.app.auth.payload.TokenResponse;
import com.substring.auth.app.auth.payload.UserDto;
import com.substring.auth.app.auth.entities.RefreshToken;
import com.substring.auth.app.auth.entities.User;
import com.substring.auth.app.auth.repositories.RefreshTokenRepository;
import com.substring.auth.app.auth.repositories.UserRepository;
import com.substring.auth.app.auth.services.AuthService;
import com.substring.auth.app.auth.services.impl.CookieService;
import com.substring.auth.app.auth.services.impl.JwtService;
import com.substring.auth.app.officer.entity.OfficerProfile;
import com.substring.auth.app.officer.repository.OfficerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ModelMapper mapper;
    private final CookieService cookieService;
    private final OfficerProfileRepository officerRepository;
    private final CacheManager cacheManager;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletResponse response
    ) {

        // Authenticate user
        authenticate(loginRequest);

        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid Username or Password")
                );

        if (!user.isEnable()) {
            throw new DisabledException("User is disabled");
        }

        // Ensure officer profile exists in PostgreSQL and refresh Redis directory
        try {
            OfficerProfile profile = officerRepository.findByMysqlUserId(user.getId())
                    .orElseGet(() -> {
                        OfficerProfile op = new OfficerProfile();
                        op.setMysqlUserId(user.getId());
                        return op;
                    });
            String desig = (user.getPolicePost() != null && !user.getPolicePost().isBlank())
                    ? user.getPolicePost().trim()
                    : "Officer";
            profile.setDesignation(desig);
            profile.setOfficerName(user.getName() != null && !user.getName().isBlank() ? user.getName() : "Officer");
            officerRepository.save(profile);
            if (cacheManager != null && cacheManager.getCache("officers_directory") != null) {
                cacheManager.getCache("officers_directory").clear();
            }
        } catch (Exception e) {
            log.warn("[CROSS-DB SYNC] Failed to sync OfficerProfile in PostgreSQL for user ID {} during login: {}",
                    user.getId(), e.getMessage());
        }

        String jti = UUID.randomUUID().toString();

        var refreshTokenOb = RefreshToken.builder()
                .jti(jti)
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(
                        Instant.now().plusSeconds(
                                jwtService.getRefreshTtlSeconds()
                        )
                )
                .revoked(false)
                .build();

        // Save refresh-token metadata
        refreshTokenRepository.save(refreshTokenOb);

        // Generate access and refresh tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(
                user,
                refreshTokenOb.getJti()
        );

        // Send refresh token only through HttpOnly cookie
        cookieService.attachRefreshCookie(
                response,
                refreshToken,
                (int) jwtService.getRefreshTtlSeconds()
        );

        cookieService.addNoStoreHeaders(response);

        // Do not expose refresh token in JSON
        TokenResponse tokenResponse = TokenResponse.of(
                accessToken,
                jwtService.getAccessTtlSeconds(),
                mapper.map(user, UserDto.class)
        );

        return ResponseEntity.ok(tokenResponse);
    }

    private Authentication authenticate(LoginRequest loginRequest) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.email(),
                            loginRequest.password()
                    )
            );
        } catch (Exception e) {
            throw new BadCredentialsException(
                    "Invalid Username or Password !!"
            );
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            HttpServletResponse response,
            HttpServletRequest request
    ) {

        String refreshToken = readRefreshTokenFromRequest(request)
                .orElseThrow(() ->
                        new BadCredentialsException("Refresh token is missing")
                );

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid Refresh Token Type");
        }

        String jti = jwtService.getJti(refreshToken);
        UUID userId = jwtService.getUserId(refreshToken);

        RefreshToken storedRefreshToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() ->
                        new BadCredentialsException("Refresh token not recognized")
                );

        if (storedRefreshToken.isRevoked()) {
            throw new BadCredentialsException(
                    "Refresh token expired or revoked"
            );
        }

        if (storedRefreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        if (!storedRefreshToken.getUser().getId().equals(userId)) {
            throw new BadCredentialsException(
                    "Refresh token does not belong to this user"
            );
        }

        // Rotate refresh token
        storedRefreshToken.setRevoked(true);

        String newJti = UUID.randomUUID().toString();

        storedRefreshToken.setReplacedByToken(newJti);

        refreshTokenRepository.save(storedRefreshToken);

        User user = storedRefreshToken.getUser();

        var newRefreshTokenOb = RefreshToken.builder()
                .jti(newJti)
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(
                        Instant.now().plusSeconds(
                                jwtService.getRefreshTtlSeconds()
                        )
                )
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshTokenOb);

        String newAccessToken = jwtService.generateAccessToken(user);

        String newRefreshToken = jwtService.generateRefreshToken(
                user,
                newRefreshTokenOb.getJti()
        );

        // Set rotated refresh token in HttpOnly cookie
        cookieService.attachRefreshCookie(
                response,
                newRefreshToken,
                (int) jwtService.getRefreshTtlSeconds()
        );

        cookieService.addNoStoreHeaders(response);

        // Return access token only
        return ResponseEntity.ok(
                TokenResponse.of(
                        newAccessToken,
                        jwtService.getAccessTtlSeconds(),
                        mapper.map(user, UserDto.class)
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        readRefreshTokenFromRequest(request).ifPresent(token -> {
            try {
                if (jwtService.isRefreshToken(token)) {
                    String jti = jwtService.getJti(token);

                    refreshTokenRepository.findByJti(jti).ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
                }
            } catch (JwtException ignored) {
                // Ignore invalid refresh tokens during logout
            }
        });

        // Clear refresh cookie
        cookieService.clearRefreshCookie(response);
        cookieService.addNoStoreHeaders(response);

        SecurityContextHolder.clearContext();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // Refresh tokens are accepted exclusively from the HttpOnly cookie.
    private Optional<String> readRefreshTokenFromRequest(
            HttpServletRequest request
    ) {

        // 1. Cookie
        if (request.getCookies() != null) {

            Optional<String> fromCookie = Arrays.stream(request.getCookies())
                    .filter(c ->
                            cookieService.getRefreshTokenCookieName()
                                    .equals(c.getName())
                    )
                    .map(Cookie::getValue)
                    .filter(v -> !v.isBlank())
                    .findFirst();

            if (fromCookie.isPresent()) {
                return fromCookie;
            }
        }

        return Optional.empty();
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(
            @RequestBody UserDto userDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerUser(userDto));
    }
}
