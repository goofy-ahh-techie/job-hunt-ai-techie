package com.jobhuntai.jobhunt_backend.auth.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String role,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthResponse bearer(UUID userId, String email, String role,
                                      String accessToken, long expiresInSeconds) {
        return new AuthResponse(userId, email, role, accessToken, "Bearer", expiresInSeconds);
    }
}
