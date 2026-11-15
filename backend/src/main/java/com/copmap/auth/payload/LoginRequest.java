package com.copmap.auth.payload;

public record LoginRequest(
        String email,
        String password
) {
}
