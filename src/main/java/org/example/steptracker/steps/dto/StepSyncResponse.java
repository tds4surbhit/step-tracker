package org.example.steptracker.steps.dto;

import java.util.List;

public record StepSyncResponse(int synced, List<StepSummaryDto> entries) {
}
