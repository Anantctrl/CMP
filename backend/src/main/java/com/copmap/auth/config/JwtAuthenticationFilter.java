package com.copmap.auth.config;

import com.copmap.auth.services.impl.JwtService;
import com.copmap.auth.helpers.UserHelper;
import com.copmap.auth.repositories.UserRepository;

import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    private static final Logger logger =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Note: Do NOT log the raw Authorization header to avoid token leakage in logs
        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            try {

                // Check whether token is an access token
                if (jwtService.isAccessToken(token)) {

                    // Parse JWT
                    Jws<Claims> parse = jwtService.parse(token);

                    Claims payload = parse.getBody();

                    // Extract user ID
                    String userId = payload.getSubject();

                    UUID userUuid = UserHelper.parseUUID(userId);

                    // Fetch user from MySQL
                    userRepository.findById(userUuid).ifPresent(user -> {

                        // Check whether user is enabled
                        if (user.isEnable()) {

                            // Get user role
                            List<GrantedAuthority> authorities =
                                    user.getRole() == null
                                            ? List.of()
                                            : List.of(
                                                new SimpleGrantedAuthority(
                                                    user.getRole().getName()
                                                )
                                            );

                            // Create authentication object
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            user,
                                            null,
                                            authorities
                                    );

                            // Set request details
                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource()
                                            .buildDetails(request)
                            );

                            // Store authentication
                            SecurityContextHolder.getContext()
                                    .setAuthentication(authentication);
                        }
                    });
                }

            } catch (ExpiredJwtException e) {

                logger.warn("JWT token expired for URI: {}", request.getRequestURI());

            } catch (JwtException | IllegalArgumentException e) {

                logger.warn("Invalid JWT token for URI: {}", request.getRequestURI());

            } catch (Exception e) {

                logger.error("JWT authentication failed for URI: {}", request.getRequestURI(), e);
            }
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
            throws ServletException {

        return request.getRequestURI().startsWith("/api/v1/auth");
    }
}
