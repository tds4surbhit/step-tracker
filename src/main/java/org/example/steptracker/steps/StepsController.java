package org.example.steptracker.steps;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.CurrentUser;
import org.example.steptracker.steps.dto.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/steps")
@RequiredArgsConstructor
public class StepsController {

    private final StepsService stepsService;

    @PostMapping("/sync")
    public StepSyncResponse sync(@Valid @RequestBody StepSyncRequest request) {
        return stepsService.sync(CurrentUser.id(), request);
    }

    @GetMapping("/today")
    public TodayStepsResponse today() {
        return stepsService.getToday(CurrentUser.id());
    }

    @GetMapping("/daily")
    public StepSummaryDto daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return stepsService.getDaily(CurrentUser.id(), date);
    }

    @GetMapping("/range")
    public StepRangeResponse range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return stepsService.getRange(CurrentUser.id(), start, end);
    }
}
