package org.example.steptracker.goal.dto;

import jakarta.validation.constraints.Positive;

public record GoalUpdateRequest(@Positive int dailyGoalSteps) {
}
