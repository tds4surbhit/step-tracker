package org.example.steptracker.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthSource {
    HEALTHKIT("healthkit"),
    HEALTH_CONNECT("health_connect"),
    SENSOR("sensor"),
    MANUAL("manual");

    private final String wireValue;

    HealthSource(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static HealthSource fromWireValue(String value) {
        for (HealthSource source : values()) {
            if (source.wireValue.equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown health source: " + value);
    }
}
