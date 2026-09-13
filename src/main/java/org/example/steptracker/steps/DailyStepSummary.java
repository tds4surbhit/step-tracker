package org.example.steptracker.steps;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.steptracker.device.HealthSource;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_step_summary")
@Getter
@Setter
@NoArgsConstructor
public class DailyStepSummary {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "step_count", nullable = false)
    private int stepCount;

    @Column(nullable = false)
    private HealthSource source;

    private String timezone;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public DailyStepSummary(UUID userId, LocalDate entryDate, int stepCount, HealthSource source, String timezone) {
        this.userId = userId;
        this.entryDate = entryDate;
        this.stepCount = stepCount;
        this.source = source;
        this.timezone = timezone;
        this.syncedAt = Instant.now();
    }

    public void applySync(int stepCount, HealthSource source, String timezone) {
        this.stepCount = stepCount;
        this.source = source;
        this.timezone = timezone;
        this.syncedAt = Instant.now();
    }
}
