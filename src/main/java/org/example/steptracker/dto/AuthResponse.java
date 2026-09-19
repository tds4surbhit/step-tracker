package org.example.steptracker.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
