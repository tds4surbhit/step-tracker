package org.example.steptracker.goal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.CurrentUser;
import org.example.steptracker.goal.dto.GoalResponse;
import org.example.steptracker.goal.dto.GoalUpdateRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public GoalResponse getGoal() {
        return goalService.getGoal(CurrentUser.id());
    }

    @PutMapping
    public GoalResponse updateGoal(@Valid @RequestBody GoalUpdateRequest request) {
        return goalService.updateGoal(CurrentUser.id(), request);
    }
}
