
package com.substring.auth.app.auth.services.impl;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@Getter
public class CookieService {

    private final String refreshTokenCookieName;
    private final boolean cookieHttpOnly;
    private final boolean cookieSecure;
    private final String cookieDomain;
    private final String cookieSameSite;

    public CookieService(
            @Value("${security.jwt.refresh-token-cookie-name}")
            String refreshTokenCookieName,

            @Value("${security.jwt.cookie-http-only}")
            boolean cookieHttpOnly,

            @Value("${security.jwt.cookie-secure}")
            boolean cookieSecure,

            @Value("${security.jwt.cookie-same-site}")
            String cookieSameSite,

            @Value("${security.jwt.cookie-domain}")
            String cookieDomain
    ) {
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.cookieHttpOnly = cookieHttpOnly;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;

        if (!cookieSameSite.equalsIgnoreCase("Strict")
                && !cookieSameSite.equalsIgnoreCase("Lax")
                && !cookieSameSite.equalsIgnoreCase("None")) {
            throw new IllegalArgumentException(
                    "Invalid SameSite cookie configuration"
            );
        }

        if (cookieSameSite.equalsIgnoreCase("None") && !cookieSecure) {
            throw new IllegalArgumentException(
                    "SameSite=None requires Secure=true"
            );
        }
    }

    // Attach refresh token as an HttpOnly cookie
    public void attachRefreshCookie(
            HttpServletResponse response,
            String value,
            int maxAge
    ) {

        // Never log the refresh token or its value.
        var responseCookieBuilder =
                ResponseCookie.from(refreshTokenCookieName, value)
                        .httpOnly(cookieHttpOnly)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(maxAge)
                        .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            responseCookieBuilder.domain(cookieDomain);
        }

        ResponseCookie responseCookie =
                responseCookieBuilder.build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                responseCookie.toString()
        );
    }

    // Clear refresh token cookie
    public void clearRefreshCookie(HttpServletResponse response) {

        var builder = ResponseCookie.from(refreshTokenCookieName, "")
                .maxAge(0)
                .httpOnly(cookieHttpOnly)
                .path("/")
                .sameSite(cookieSameSite)
                .secure(cookieSecure);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        ResponseCookie responseCookie = builder.build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                responseCookie.toString()
        );
    }

    // Prevent sensitive authentication responses from being cached
    public void addNoStoreHeaders(HttpServletResponse response) {

        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store"
        );

        response.setHeader(
                "Pragma",
                "no-cache"
        );
    }
}
