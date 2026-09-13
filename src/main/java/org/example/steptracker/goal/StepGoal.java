package org.example.steptracker.goal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_goal")
@Getter
@Setter
@NoArgsConstructor
public class StepGoal {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "daily_goal_steps", nullable = false)
    private int dailyGoalSteps;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StepGoal(UUID userId, int dailyGoalSteps) {
        this.userId = userId;
        this.dailyGoalSteps = dailyGoalSteps;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
