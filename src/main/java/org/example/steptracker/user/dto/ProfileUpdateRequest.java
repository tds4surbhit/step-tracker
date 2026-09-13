package org.example.steptracker.user.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileUpdateRequest(
        String displayName,
        LocalDate dateOfBirth,
        @Positive Integer heightCm,
        @Positive BigDecimal weightKg,
        String gender
) {
}
