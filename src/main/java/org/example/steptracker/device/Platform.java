package org.example.steptracker.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Platform {
    IOS("ios"),
    ANDROID("android");

    private final String wireValue;

    Platform(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static Platform fromWireValue(String value) {
        for (Platform platform : values()) {
            if (platform.wireValue.equalsIgnoreCase(value)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("Unknown platform: " + value);
    }
}
