package org.example.steptracker.goal;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.goal.dto.GoalResponse;
import org.example.steptracker.goal.dto.GoalUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    public static final int DEFAULT_DAILY_GOAL_STEPS = 10_000;

    private final StepGoalRepository stepGoalRepository;

    @Transactional
    public void createDefaultGoal(UUID userId) {
        stepGoalRepository.save(new StepGoal(userId, DEFAULT_DAILY_GOAL_STEPS));
    }

    public GoalResponse getGoal(UUID userId) {
        return new GoalResponse(resolveGoal(userId).getDailyGoalSteps());
    }

    @Transactional
    public GoalResponse updateGoal(UUID userId, GoalUpdateRequest request) {
        StepGoal goal = resolveGoal(userId);
        goal.setDailyGoalSteps(request.dailyGoalSteps());
        return new GoalResponse(goal.getDailyGoalSteps());
    }

    public int currentGoalSteps(UUID userId) {
        return resolveGoal(userId).getDailyGoalSteps();
    }

    private StepGoal resolveGoal(UUID userId) {
        return stepGoalRepository.findById(userId)
                .orElseGet(() -> stepGoalRepository.save(new StepGoal(userId, DEFAULT_DAILY_GOAL_STEPS)));
    }
}
