package org.example.steptracker.steps.dto;

import org.example.steptracker.device.HealthSource;

import java.time.LocalDate;

public record StepSummaryDto(
        LocalDate date,
        int stepCount,
        HealthSource source
) {
}
