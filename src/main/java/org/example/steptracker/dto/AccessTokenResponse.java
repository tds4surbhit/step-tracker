package org.example.steptracker.dto;

public record AccessTokenResponse(
        String accessToken,
        long expiresIn
) {
}
