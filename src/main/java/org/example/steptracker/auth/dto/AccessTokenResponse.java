package org.example.steptracker.auth.dto;

public record AccessTokenResponse(
        String accessToken,
        long expiresIn
) {
}
