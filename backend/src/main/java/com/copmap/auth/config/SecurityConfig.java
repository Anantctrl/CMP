package com.copmap.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.copmap.dtos.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(Customizer.withDefaults())

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()

                        // Public URLs: Registration, Login, etc.
                        .requestMatchers(AppConstants.AUTH_PUBLIC_URLS).permitAll()

                        // Restricted URLs - Accessible by Supervisors and Administrators
                        .requestMatchers(AppConstants.AUTH_RESTRICTED_URLS).hasAnyAuthority("Supervisor", "Administrator")

                        // All other URLs require authentication
                        .anyRequest().authenticated()
                )

                // Disable default logout
                .logout(AbstractHttpConfigurer::disable)

                // Exception handling
                .exceptionHandling(ex -> ex

                        // 401 Unauthorized
                        .authenticationEntryPoint((request, response, e) -> {

                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");

                            String message = e.getMessage();

                            String error = (String) request.getAttribute("error");

                            if (error != null) {
                                message = error;
                            }

                            var apiError = ApiError.of(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    "Unauthorized Access",
                                    message,
                                    request.getRequestURI(),
                                    true
                            );

                            var objectMapper = new ObjectMapper();

                            response.getWriter().write(
                                    objectMapper.writeValueAsString(apiError)
                            );
                        })

                        // 403 Forbidden
                        .accessDeniedHandler((request, response, e) -> {

                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");

                            String message = e.getMessage();

                            String error = (String) request.getAttribute("error");

                            if (error != null) {
                                message = error;
                            }

                            var apiError = ApiError.of(
                                    HttpStatus.FORBIDDEN.value(),
                                    "Forbidden Access",
                                    message,
                                    request.getRequestURI(),
                                    true
                            );

                            var objectMapper = new ObjectMapper();

                            response.getWriter().write(
                                    objectMapper.writeValueAsString(apiError)
                            );
                        })
                )

                // JWT Authentication Filter
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.front-end-url}") String corsUrls
    ) {
        List<String> origins = Arrays.stream(corsUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
        );
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
