package org.example.steptracker.steps.dto;

import java.time.LocalDate;

public record TodayStepsResponse(LocalDate date, int stepCount, int goal) {
}
