package org.example.steptracker.steps.dto;

import java.util.List;

public record StepRangeResponse(List<StepSummaryDto> entries) {
}
