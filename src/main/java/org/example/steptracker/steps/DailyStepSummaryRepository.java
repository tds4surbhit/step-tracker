package org.example.steptracker.steps;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyStepSummaryRepository extends JpaRepository<DailyStepSummary, UUID> {
    Optional<DailyStepSummary> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);
    List<DailyStepSummary> findByUserIdAndEntryDateBetweenOrderByEntryDateAsc(UUID userId, LocalDate start, LocalDate end);
}
