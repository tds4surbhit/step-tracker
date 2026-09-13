package org.example.steptracker.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String email,
        String displayName,
        LocalDate dateOfBirth,
        Integer heightCm,
        BigDecimal weightKg,
        String gender
) {
}
