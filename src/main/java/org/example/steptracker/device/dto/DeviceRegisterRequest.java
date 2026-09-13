package org.example.steptracker.device.dto;

import jakarta.validation.constraints.NotNull;
import org.example.steptracker.device.HealthSource;
import org.example.steptracker.device.Platform;

public record DeviceRegisterRequest(
        @NotNull Platform platform,
        @NotNull HealthSource healthSource
) {
}
