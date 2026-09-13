package org.example.steptracker.steps;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.BadRequestException;
import org.example.steptracker.common.NotFoundException;
import org.example.steptracker.device.Device;
import org.example.steptracker.device.DeviceRepository;
import org.example.steptracker.goal.GoalService;
import org.example.steptracker.steps.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StepsService {

    private static final int MAX_RANGE_DAYS = 90;

    private final DailyStepSummaryRepository stepSummaryRepository;
    private final DeviceRepository deviceRepository;
    private final GoalService goalService;

    @Transactional
    public StepSyncResponse sync(UUID userId, StepSyncRequest request) {
        Device device = deviceRepository.findById(request.deviceId())
                .orElseThrow(() -> new NotFoundException("Device not found"));
        if (!device.getUserId().equals(userId)) {
            throw new NotFoundException("Device not found");
        }

        List<StepSummaryDto> stored = request.entries().stream()
                .map(entry -> upsert(userId, entry))
                .toList();

        device.setLastSyncedAt(Instant.now());
        deviceRepository.save(device);

        return new StepSyncResponse(stored.size(), stored);
    }

    private StepSummaryDto upsert(UUID userId, StepEntryDto entry) {
        DailyStepSummary summary = stepSummaryRepository.findByUserIdAndEntryDate(userId, entry.date())
                .orElseGet(() -> new DailyStepSummary(userId, entry.date(), entry.stepCount(), entry.source(), entry.timezone()));
        summary.applySync(entry.stepCount(), entry.source(), entry.timezone());
        summary = stepSummaryRepository.save(summary);
        return toSummaryDto(summary);
    }

    public TodayStepsResponse getToday(UUID userId) {
        LocalDate today = LocalDate.now();
        int stepCount = stepSummaryRepository.findByUserIdAndEntryDate(userId, today)
                .map(DailyStepSummary::getStepCount)
                .orElse(0);
        int goal = goalService.currentGoalSteps(userId);
        return new TodayStepsResponse(today, stepCount, goal);
    }

    public StepSummaryDto getDaily(UUID userId, LocalDate date) {
        return stepSummaryRepository.findByUserIdAndEntryDate(userId, date)
                .map(this::toSummaryDto)
                .orElseThrow(() -> new NotFoundException("No step data for " + date));
    }

    public StepRangeResponse getRange(UUID userId, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadRequestException("end date must not be before start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        List<StepSummaryDto> entries = stepSummaryRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, start, end)
                .stream()
                .map(this::toSummaryDto)
                .toList();
        return new StepRangeResponse(entries);
    }

    private StepSummaryDto toSummaryDto(DailyStepSummary summary) {
        return new StepSummaryDto(summary.getEntryDate(), summary.getStepCount(), summary.getSource());
    }
}
