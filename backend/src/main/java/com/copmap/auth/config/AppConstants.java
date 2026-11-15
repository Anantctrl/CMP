package com.copmap.auth.config;

public class AppConstants {

    public static final String[] AUTH_PUBLIC_URLS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    public static final String[] AUTH_RESTRICTED_URLS = {
            "/api/v1/users/**"
    };

    public static final String DEFAULT_ROLE = "Not_Assigned";
}
