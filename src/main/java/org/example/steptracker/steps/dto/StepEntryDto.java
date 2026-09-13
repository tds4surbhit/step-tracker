package org.example.steptracker.steps.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.steptracker.device.HealthSource;

import java.time.LocalDate;

public record StepEntryDto(
        @NotNull LocalDate date,
        @PositiveOrZero int stepCount,
        @NotNull HealthSource source,
        String timezone
) {
}
