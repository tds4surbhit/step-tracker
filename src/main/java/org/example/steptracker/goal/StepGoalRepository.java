package org.example.steptracker.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StepGoalRepository extends JpaRepository<StepGoal, UUID> {
}
